package com.xlythe.view.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.xlythe.math.Point;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/** The numbers on the graph follow the font size the reader has chosen. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "mdpi")
public class TextSizeTest {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 400;

    private GraphView newGraph() {
        GraphView graph = new GraphView(ApplicationProvider.getApplicationContext());
        graph.setBackgroundColor(Color.WHITE);
        graph.setTextColor(Color.BLACK);
        graph.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        graph.layout(0, 0, WIDTH, HEIGHT);
        return graph;
    }

    private Bitmap render(GraphView graph) {
        Bitmap bitmap = Bitmap.createBitmap(graph.getWidth(), graph.getHeight(), Bitmap.Config.ARGB_8888);
        graph.draw(new Canvas(bitmap));
        return bitmap;
    }

    /** How many pixels of the row are darker than the background, ie. how much text is on it. */
    private static int inkIn(Bitmap bitmap, int row) {
        int ink = 0;
        for (int x = 0; x < bitmap.getWidth(); x++) {
            if (Color.red(bitmap.getPixel(x, row)) < 200) {
                ink++;
            }
        }
        return ink;
    }

    /** The row through the middle of the numbers written along the top of the graph. */
    private static final int LABEL_ROW = 12;

    @Test
    public void theNumbers_shrinkWithTheChosenFontSize() {
        int atNormalSize = inkIn(render(newGraph()), LABEL_ROW);

        RuntimeEnvironment.setFontScale(0.75f);
        int atSmallSize = inkIn(render(newGraph()), LABEL_ROW);

        assertTrue("the numbers ignored the font size: " + atNormalSize + "px of ink became "
                + atSmallSize + "px", atSmallSize < atNormalSize);
    }

    /**
     * Upwards they stop at the width of the band they are written in. Past that they run into each
     * other and out over the graph, which helps nobody.
     */
    @Test
    public void theNumbers_stopGrowingWhereTheyWouldNotFit() {
        RuntimeEnvironment.setFontScale(1.5f);
        int atLargeSize = inkIn(render(newGraph()), LABEL_ROW);

        RuntimeEnvironment.setFontScale(3f);
        int atHugeSize = inkIn(render(newGraph()), LABEL_ROW);

        assertEquals(atLargeSize, atHugeSize);
    }

    @Test
    public void theReadout_growsWithTheChosenFontSize() {
        RuntimeEnvironment.setFontScale(1.5f);
        GraphView graph = newGraph();
        graph.setInspectionEnabled(true);
        graph.addGraph(line());

        int atLargeSize = widthOfTheReadout(graph);

        RuntimeEnvironment.setFontScale(1f);
        GraphView normal = newGraph();
        normal.setInspectionEnabled(true);
        normal.addGraph(line());

        assertTrue("the readout ignored the font size", atLargeSize > widthOfTheReadout(normal));
    }

    /** Taps the curve and measures how wide the readout card comes out. */
    private int widthOfTheReadout(GraphView graph) {
        graph.onTouchEvent(android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN,
                graph.toPixelX(2), graph.toPixelY(2), 0));
        graph.onTouchEvent(android.view.MotionEvent.obtain(0, 1, android.view.MotionEvent.ACTION_UP,
                graph.toPixelX(2), graph.toPixelY(2), 0));
        Bitmap bitmap = render(graph);

        // The card is the run of white sitting on the row above the ring.
        int row = (int) graph.toPixelY(2) - 24;
        int widest = 0;
        int run = 0;
        for (int x = 0; x < bitmap.getWidth(); x++) {
            run = bitmap.getPixel(x, row) == Color.WHITE ? run + 1 : 0;
            widest = Math.max(widest, run);
        }
        return widest;
    }

    private static GraphView.Graph line() {
        List<Point> data = new ArrayList<>();
        for (int step = -100; step <= 100; step++) {
            data.add(new Point(step / 10f, step / 10f));
        }
        return new GraphView.Graph("Y=X", Color.RED, data);
    }
}
