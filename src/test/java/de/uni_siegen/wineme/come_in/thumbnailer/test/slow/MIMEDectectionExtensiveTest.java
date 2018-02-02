package de.uni_siegen.wineme.come_in.thumbnailer.test.slow;

import de.uni_siegen.wineme.come_in.thumbnailer.test.TestConfiguration;
import de.uni_siegen.wineme.come_in.thumbnailer.util.mime.MimeTypeDetector;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;
import uk.ac.lkl.common.util.testing.LabelledParameterized;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.fail;

// Foreach Filename in TestFileDirectory : combine extensions.
@RunWith(LabelledParameterized.class)
// Strange as it sounds, these tests only run inside Eclipse. In Ant, the @RunWith-Annotation seems to go ignored. I'd really like to know why!
public class MIMEDectectionExtensiveTest implements TestConfiguration {
    private MimeTypeDetector mimeType;
    private File file;
    private String expectedMime;

    public MIMEDectectionExtensiveTest(String name, String expectedMime, File file) {
        this.file = file;
        this.expectedMime = expectedMime;
    }

    @Before
    public void setUp() throws Exception {
        mimeType = new MimeTypeDetector();
    }

    @Test
    public void testMime() {
        String mime = mimeType.getMimeType(file);
        if (!expectedMime.equalsIgnoreCase(mime))
            fail("File " + file.getName() + ": Mime is not equal: expected \"" + expectedMime + "\", but was \"" + mime + "\".");
    }

    @Parameters
    public static Collection<Object[]> suite() throws Exception {
        MimeTypeDetector mimeType = new MimeTypeDetector();

        File path = new File(TESTFILES_DIR);
        File tmpDir = File.createTempFile("test-mime-detection", "");
        File[] testfiles = path.listFiles();

        tmpDir.delete();
        tmpDir.mkdirs();

        ArrayList<String> extensions = new ArrayList<String>(testfiles.length);
        for (File input : testfiles) {
            if (input.isDirectory() || input.isHidden())
                continue;

            extensions.add(FilenameUtils.getExtension(input.getName()));
        }
        extensions.add("");

        Collection<Object[]> param = new ArrayList<Object[]>();
        for (File input : testfiles) {
            if (input.isDirectory() || input.isHidden())
                continue;

            String myName = FilenameUtils.getBaseName(input.getName());
            String myExt = FilenameUtils.getExtension(input.getName());
            final String myMime = mimeType.getMimeType(input);

            for (String otherExt : extensions) {
                String newFilename = tmpDir.getAbsolutePath() + File.separator + myName + "-" + myExt;
                if (!otherExt.isEmpty())
                    newFilename += "." + otherExt;

                File output = new File(newFilename);
                try {
                    FileUtils.copyFile(input, output);

                    param.add(new Object[]{"test_mime_" + myExt + "_" + otherExt, myMime, output});
                } catch (IOException e) {
                    System.err.println("Warning: Filename " + output.getName() + " is could not be written.");
                }
            }
        }

        return param;
    }
}
