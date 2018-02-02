package edu.mit.scratch;

// Decompiled by:       Fernflower v0.6
// Date:                26.11.2010 23:09:28
// Copyright:           2008-2009, Stiver
// Home page:           http://www.reversed-java.com

import java.awt.*;

interface Drawable {

    boolean isShowing();

    Rectangle rect();

    Rectangle fullRect();

    void paint(Graphics var1);

    void paintBubble(Graphics var1);

    void dragTo(int var1, int var2);

    void mouseDown(int var1, int var2);
}
