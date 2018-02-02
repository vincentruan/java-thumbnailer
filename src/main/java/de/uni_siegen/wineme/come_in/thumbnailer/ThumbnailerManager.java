/*
 * regain/Thumbnailer - A file search engine providing plenty of formats (Plugin)
 * Copyright (C) 2011  Come_IN Computerclubs (University of Siegen)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Contact: Come_IN-Team <come_in-team@listserv.uni-siegen.de>
 */

package de.uni_siegen.wineme.come_in.thumbnailer;

import de.uni_siegen.wineme.come_in.thumbnailer.thumbnailers.Thumbnailer;
import de.uni_siegen.wineme.come_in.thumbnailer.util.ChainedHashMap;
import de.uni_siegen.wineme.come_in.thumbnailer.util.IOUtil;
import de.uni_siegen.wineme.come_in.thumbnailer.util.StringUtil;
import de.uni_siegen.wineme.come_in.thumbnailer.util.mime.MimeTypeDetector;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * This class manages all available Thumbnailers.
 * Its purpose is to delegate a File to the appropriate Thumbnailer in order to get a Thumbnail of it.
 * This is done in a fall-through manner: If several Thumbnailer can handle a specific filetype,
 * all are tried until a Thumbnail could be created.
 * <p>
 * Fill this class with available Thumbnailers via the registerThumbnailer()-Method.
 * Then call generateThumbnail().
 *
 * @author Benjamin
 */
public class ThumbnailerManager implements Thumbnailer, ThumbnailerConstants {

    /**
     * @var Starting estimate of the number of mime types that the thumbnailer can manager
     */
    private static final int DEFAULT_NB_MIME_TYPES = 40;
    /**
     * @var Starting estimate of the number of thumbnailers per mime type
     */
    private static final int DEFAULT_NB_THUMBNAILERS_PER_MIME = 5;

    /**
     * @var MIME Type for "all MIME" within thumbnailers Hash
     */
    private static final String ALL_MIME_WILDCARD = "*/*";
    /**
     * The logger for this class
     */
    private static Logger mLog = Logger.getLogger(ThumbnailerManager.class);
    /**
     * @var Width of thumbnail picture to create (in Pixel)
     */
    private int thumbWidth;
    /**
     * @var Height of thumbnail picture to create (in Pixel)
     */
    private int thumbHeight;
    /**
     * @var Options for image resizer (currently unused)
     */
    private int thumbOptions = 0;
    /**
     * Folder under which new thumbnails should be filed
     */
    private File thumbnailFolder;
    /**
     * Thumbnailers per MIME-Type they accept (ALL_MIME_WILDCARD for all)
     */
    private ChainedHashMap<String, Thumbnailer> thumbnailers;

    /**
     * All Thumbnailers.
     */
    private Queue<Thumbnailer> allThumbnailers;

    /**
     * Magic Mime Detection ... a wrapper class to Aperature's Mime thingies.
     */
    private MimeTypeDetector mimeTypeDetector;

    /**
     * Initialise Thumbnail Manager
     */
    public ThumbnailerManager() {
        // Execute close() when JVM shuts down (if it wasn't executed before).
        final ThumbnailerManager self = this;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                IOUtil.quietlyClose(self);
            }
        });

        thumbnailers = new ChainedHashMap<String, Thumbnailer>(DEFAULT_NB_MIME_TYPES, DEFAULT_NB_THUMBNAILERS_PER_MIME);
        allThumbnailers = new LinkedList<Thumbnailer>();

        mimeTypeDetector = new MimeTypeDetector();

        thumbHeight = THUMBNAIL_DEFAULT_HEIGHT;
        thumbWidth = THUMBNAIL_DEFAULT_WIDTH;
    }
/* currently not used
	private static String generate_hash(String str)
	{
		return StringUtil.transpose_string(StringUtil.md5(str));
	}
*/

    /**
     * Calculate a thumbnail filename (via hashing).
     *
     * @param input      Input file
     * @param checkExist If true: guarantuee that such a filename doesn't exist yet
     * @return The chosen filename
     */
    public File chooseThumbnailFilename(File input, boolean checkExist) {
        if (thumbnailFolder == null) {
            throw new RuntimeException("chooseThumbnailFilename cannot be run before a first call to setThumbnailFolder()");
        }
        if (input == null) {
            throw new NullPointerException("Input file may not be null");
        }

        String hash = ""; //"_" + generate_hash(input.getAbsolutePath());
        String prefix = input.getName().replace('.', '_');

        int tries = 0;
        String suffix = "";
        File output;
        do {
            if (tries > 0) {
                int suffix_length = tries / 4 + 1; // Simple (i.e. guessed) heuristic to add randomness if many files have the same name
                suffix = "-" + StringUtil.randomString(suffix_length);
            }

            String name = prefix + hash + suffix + ".png";
            output = new File(thumbnailFolder, name);

            tries++;
        }
        while (checkExist && output.exists());

        return output;
    }

    /**
     * Set the folder where the thumbnails should be generated by default
     * (if no output file is given).
     *
     * @param thumbnailPath Path where the future thumbnails will be written to
     * @throws FileDoesNotExistException If the given path is not writeable
     */
    public void setThumbnailFolder(String thumbnailPath) throws FileDoesNotExistException {
        setThumbnailFolder(new File(thumbnailPath));
    }

    /**
     * Set the folder where the thumbnails should be generated by default
     * (if no output file is given).
     *
     * @param thumbnailPath Path where the future thumbnails will be written to
     * @throws FileDoesNotExistException If the given path is not writeable
     */
    public void setThumbnailFolder(File thumbnailPath) throws FileDoesNotExistException {
        FileDoesNotExistException.checkWrite(thumbnailPath, "The thumbnail folder", true, true);

        thumbnailFolder = thumbnailPath;
    }

    /**
     * Generate a Thumbnail.
     * The output file name is generated using a hashing scheme.
     * It is garantueed that an existing Thumbnail is not overwritten by this.
     *
     * @param input Input file that should be processed.
     * @return Name of Thumbnail-File generated.
     * @throws FileDoesNotExistException
     * @throws IOException
     * @throws ThumbnailerException
     */
    public File createThumbnail(File input) throws FileDoesNotExistException, IOException, ThumbnailerException {
        File output = chooseThumbnailFilename(input, true);
        generateThumbnail(input, output);

        return output;
    }

    /**
     * Add a Thumbnailer-Class to the list of available Thumbnailers
     * Note that the order you add Thumbnailers may make a difference:
     * First added Thumbnailers are tried first, if one fails, the next
     * (that claims to be able to treat such a document) is tried.
     * (Thumbnailers that claim to treat all MIME Types are tried last, though.)
     *
     * @param thumbnailer Thumbnailer to add.
     */
    public void registerThumbnailer(Thumbnailer thumbnailer) {
        String[] acceptMIME = thumbnailer.getAcceptedMIMETypes();
        if (acceptMIME == null) {
            thumbnailers.put(ALL_MIME_WILDCARD, thumbnailer);
        } else {
            for (String mime : acceptMIME) {
                thumbnailers.put(mime, thumbnailer);
            }
        }
        allThumbnailers.add(thumbnailer);

        thumbnailer.setImageSize(thumbWidth, thumbHeight, thumbOptions);
    }

    /**
     * Instead of a deconstructor:
     * De-initialize ThumbnailManager and its thumbnailers.
     * <p>
     * This functions should be called before termination of the program,
     * and Thumbnails can't be generated after calling this function.
     */
    @Override
    public void close() {
        if (allThumbnailers == null) {
            return; // Already closed
        }

        for (Thumbnailer thumbnailer : allThumbnailers) {
            try {
                thumbnailer.close();
            } catch (IOException e) {
                mLog.error("Error during close of Thumbnailer:", e);
            }
        }

        thumbnailers = null;
        allThumbnailers = null;
    }

    /**
     * Generate a Thumbnail of the input file.
     * Try all available Thumbnailers and use the first that returns an image.
     * <p>
     * MIME-Detection narrows the selection of Thumbnailers to try:
     * <li>First all Thumbnailers that declare to accept such a MIME Type are used
     * <li>Then all Thumbnailers that declare to accept all possible MIME Types.
     *
     * @param input    Input file that should be processed
     * @param output   File in which should be written
     * @param mimeType MIME-Type of input file (null if unknown)
     * @throws IOException          If file cannot be read/written.
     * @throws ThumbnailerException If the thumbnailing process failed
     *                              (i.e., no thumbnailer could generate an Thumbnail.
     *                              The last ThumbnailerException is re-thrown.)
     */
    @Override
    public void generateThumbnail(File input, File output, String mimeType) throws IOException, ThumbnailerException {
        FileDoesNotExistException.check(input, "The input file");
        FileDoesNotExistException.checkWrite(output, "The output file", true, false);

        boolean generated = false;

        // MIME might be known already (in case of recursive thumbnail managers)
        if (mimeType == null) {
            mimeType = mimeTypeDetector.getMimeType(input);
            mLog.debug("Detected Mime-Typ: " + mimeType);
        }

        if (mimeType != null) {
            generated = executeThumbnailers(mimeType, input, output, mimeType);
        }

        // Try again using wildcard thumbnailers
        if (!generated) {
            generated = executeThumbnailers(ALL_MIME_WILDCARD, input, output, mimeType);
        }

        if (!generated) {
            throw new ThumbnailerException("No suitable Thumbnailer has been found. (File: " + input.getName() + " ; Detected MIME: " + mimeType + ")");
        }
    }

    /**
     * Generate a Thumbnail of the input file.
     * Try all available Thumbnailers and use the first that returns an image.
     * <p>
     * MIME-Detection narrows the selection of Thumbnailers to try:
     * <li>First all Thumbnailers that declare to accept such a MIME Type are used
     * <li>Then all Thumbnailers that declare to accept all possible MIME Types.
     *
     * @param input  Input file that should be processed
     * @param output File in which should be written
     * @throws IOException          If file cannot be read/written.
     * @throws ThumbnailerException If the thumbnailing process failed
     *                              (i.e., no thumbnailer could generate an Thumbnail.
     *                              The last ThumbnailerException is re-thrown.)
     */
    @Override
    public void generateThumbnail(File input, File output) throws IOException, ThumbnailerException {
        generateThumbnail(input, output, null);
    }

    /**
     * Helper function for Thumbnail generation:
     * execute all thumbnailers of a given MimeType.
     *
     * @param useMimeType      Which MIME Type the thumbnailers should be taken from
     * @param input            Input File that should be processed
     * @param output           Output file where the image shall be written.
     * @param detectedMimeType MIME Type that was returned by automatic MIME Detection
     * @return True on success (1 thumbnailer could generate the output file).
     * @throws IOException Input file cannot be read, or output file cannot be written, or necessary temporary files could not be created.
     */
    private boolean executeThumbnailers(String useMimeType, File input, File output, String detectedMimeType) throws IOException {
        for (Thumbnailer thumbnailer : thumbnailers.getIterable(useMimeType)) {
            try {
                thumbnailer.generateThumbnail(input, output, detectedMimeType);
                return true;
            } catch (ThumbnailerException e) {
                // This Thumbnailer apparently wasn't suitable, so try next
                mLog.warn("Warning: " + thumbnailer.getClass().getName() + " could not handle the file " + input.getName() + " (trying next)", e);
            }
        }
        return false;
    }

    /**
     * Set the image size of all following thumbnails.
     * <p>
     * ThumbnailManager delegates this to all his containing Thumbailers.
     */
    @Override
    public void setImageSize(int width, int height, int imageResizeOptions) {
        thumbHeight = height;
        thumbWidth = width;
        thumbOptions = imageResizeOptions;

        if (thumbWidth < 0) {
            thumbWidth = 0;
        }
        if (thumbHeight < 0) {
            thumbHeight = 0;
        }

        for (Thumbnailer thumbnailer : allThumbnailers) {
            thumbnailer.setImageSize(thumbWidth, thumbHeight, thumbOptions);
        }
    }

    /**
     * Get the currently set Image Width of this Thumbnailer.
     *
     * @return image width of created thumbnails.
     */
    @Override
    public int getCurrentImageWidth() {
        return thumbWidth;
    }

    /**
     * Get the currently set Image Height of this Thumbnailer.
     *
     * @return image height of created thumbnails.
     */
    @Override
    public int getCurrentImageHeight() {
        return thumbHeight;
    }


    /**
     * Summarize all contained MIME Type Thumbnailers.
     *
     * @return All accepted MIME Types, null if any.
     */
    @Override
    public String[] getAcceptedMIMETypes() {
        if (thumbnailers.containsKey(ALL_MIME_WILDCARD)) {
            return null; // All MIME Types
        } else {
            return thumbnailers.keySet().toArray(new String[]{});
        }
    }

}
