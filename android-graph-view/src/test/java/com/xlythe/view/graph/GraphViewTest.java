package com.xlythe.view.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Renders the view to a bitmap and looks at the pixels, because most of what can go wrong here
 * goes wrong in drawing: a line in the wrong colour, or the wrong thickness, or missing.
 *
 * <p>At mdpi a dp is a pixel, which makes the geometry predictable: grid lines land every 25px and,
 * on a 400x400 view, the origin lands at (200, 200).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "mdpi")
public class GraphViewTest {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 400;

    /**
     * A row and a column inside the grid, clear of the 25px label gutter, of the origin lines at
     * 200, and of the grid lines themselves (which fall on multiples of 25).
     */
    private static final int OFF_AXIS_ROW = 394;
    private static final int OFF_AXIS_COLUMN = 44;

    private GraphView newGraph() {
        GraphView graph = new GraphView(ApplicationProvider.getApplicationContext());
        graph.setBackgroundColor(Color.WHITE);
        // The outline is drawn in the grid colour and is thicker than either kind of line, so it
        // would be picked up as the widest run. Nothing here is about the outline.
        graph.setShowOutline(false);
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

    /** How many pixels wide the widest vertical line of this colour is, scanning one row. */
    private static int widestVerticalLine(Bitmap bitmap, int color, int row) {
        return widestRun(bitmap, color, row, true);
    }

    /** How many pixels tall the widest horizontal line of this colour is, scanning one column. */
    private static int widestHorizontalLine(Bitmap bitmap, int color, int column) {
        return widestRun(bitmap, color, column, false);
    }

    private static int widestRun(Bitmap bitmap, int color, int line, boolean horizontalScan) {
        int length = horizontalScan ? bitmap.getWidth() : bitmap.getHeight();
        int widest = 0;
        int run = 0;
        for (int i = 0; i < length; i++) {
            int pixel = horizontalScan ? bitmap.getPixel(i, line) : bitmap.getPixel(line, i);
            run = belongsTo(pixel, color) ? run + 1 : 0;
            widest = Math.max(widest, run);
        }
        return widest;
    }

    /**
     * A one pixel line never lands squarely on a pixel, so its colour arrives blended with the
     * background. Count a pixel as part of the line when it is nearer the line's colour than the
     * background's, and ignore anything grey, which is the background or one of the numbers.
     */
    private static boolean belongsTo(int pixel, int color) {
        int red = Color.red(pixel);
        int green = Color.green(pixel);
        int blue = Color.blue(pixel);
        int colourfulness = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
        if (colourfulness < 64) {
            return false;
        }
        return distance(pixel, color) < distance(pixel, Color.WHITE);
    }

    private static int distance(int a, int b) {
        return Math.abs(Color.red(a) - Color.red(b))
                + Math.abs(Color.green(a) - Color.green(b))
                + Math.abs(Color.blue(a) - Color.blue(b));
    }

    @Test
    public void theOriginLines_areDrawnInTheAxisColor() {
        GraphView graph = newGraph();
        graph.setGridColor(Color.RED);
        graph.setAxisColor(Color.BLUE);

        Bitmap bitmap = render(graph);

        assertTrue("the vertical origin line is not drawn in the axis colour",
                widestVerticalLine(bitmap, Color.BLUE, OFF_AXIS_ROW) > 0);
        assertTrue("the horizontal origin line is not drawn in the axis colour",
                widestHorizontalLine(bitmap, Color.BLUE, OFF_AXIS_COLUMN) > 0);
    }

    @Test
    public void theOriginLines_areBolderThanTheGridLines() {
        GraphView graph = newGraph();
        graph.setGridColor(Color.RED);
        graph.setAxisColor(Color.BLUE);
        graph.setGridSize(1);
        graph.setAxisSize(5);

        Bitmap bitmap = render(graph);

        int origin = widestVerticalLine(bitmap, Color.BLUE, OFF_AXIS_ROW);
        int grid = widestVerticalLine(bitmap, Color.RED, OFF_AXIS_ROW);
        assertTrue("the origin line (" + origin + "px) is no bolder than a grid line (" + grid + "px)",
                origin > grid);
    }

    /**
     * The mini graph hides the grid and fades its colour out to transparent, so an axis drawn with
     * the grid's paint disappeared entirely.
     */
    @Test
    public void theOriginLines_areDrawnEvenWhenTheGridIsHidden() {
        GraphView graph = newGraph();
        graph.setShowGrid(false);
        graph.setGridColor(Color.TRANSPARENT);
        graph.setAxisColor(Color.BLUE);

        Bitmap bitmap = render(graph);

        assertTrue("the vertical origin line went missing with the grid",
                widestVerticalLine(bitmap, Color.BLUE, OFF_AXIS_ROW) > 0);
        assertTrue("the horizontal origin line went missing with the grid",
                widestHorizontalLine(bitmap, Color.BLUE, OFF_AXIS_COLUMN) > 0);
        assertEquals("a grid line was drawn while the grid was hidden",
                0, widestVerticalLine(bitmap, Color.RED, OFF_AXIS_ROW));
    }

    /**
     * Panning kept its leftover pixels without ever rolling whole grid lines out of them, so a pan
     * made of many small steps pushed every line off the side of the view.
     */
    @Test
    public void panningInSmallSteps_keepsTheGridOnScreen() {
        GraphView graph = newGraph();
        graph.setGridColor(Color.RED);
        graph.setAxisColor(Color.BLUE);

        for (int i = 0; i < 100; i++) {
            graph.panBy(7, 7);
        }
        Bitmap bitmap = render(graph);

        assertTrue("the vertical grid lines slid off screen",
                widestVerticalLine(bitmap, Color.RED, OFF_AXIS_ROW) > 0);
        assertTrue("the horizontal grid lines slid off screen",
                widestHorizontalLine(bitmap, Color.RED, OFF_AXIS_COLUMN) > 0);
    }

    @Test
    public void theGridLines_areDrawnInTheGridColor() {
        GraphView graph = newGraph();
        graph.setGridColor(Color.RED);
        graph.setAxisColor(Color.BLUE);

        Bitmap bitmap = render(graph);

        assertTrue("no vertical grid lines were drawn",
                widestVerticalLine(bitmap, Color.RED, OFF_AXIS_ROW) > 0);
        assertTrue("no horizontal grid lines were drawn",
                widestHorizontalLine(bitmap, Color.RED, OFF_AXIS_COLUMN) > 0);
    }
}
