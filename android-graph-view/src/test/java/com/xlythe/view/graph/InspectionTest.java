package com.xlythe.view.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.xlythe.math.Point;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/** Tapping a graph to read a value, a slope, or the area underneath it. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "mdpi")
public class InspectionTest {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 400;

    private GraphView newGraph() {
        GraphView graph = new GraphView(ApplicationProvider.getApplicationContext());
        graph.setBackgroundColor(Color.WHITE);
        graph.setInspectionEnabled(true);
        graph.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        graph.layout(0, 0, WIDTH, HEIGHT);
        return graph;
    }

    /** y = slope * x, sampled the way the calculator samples it. */
    private static GraphView.Graph line(float slope, int color) {
        List<Point> data = new ArrayList<>();
        for (int step = -100; step <= 100; step++) {
            float x = step / 10f;
            data.add(new Point(x, slope * x));
        }
        return new GraphView.Graph("Y=" + slope + "X", color, data);
    }

    /** Touches the pixel the graph's (x, y) falls on, and lifts off again without moving. */
    private static void tap(GraphView graph, float x, float y) {
        float pixelX = graph.toPixelX(x);
        float pixelY = graph.toPixelY(y);
        graph.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, pixelX, pixelY, 0));
        graph.onTouchEvent(MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, pixelX, pixelY, 0));
    }

    /** Presses on the graph's (x, y), drags sideways to {@code toX}, and lifts off. */
    private static void drag(GraphView graph, float x, float y, float toX) {
        float pixelY = graph.toPixelY(y);
        graph.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, graph.toPixelX(x), pixelY, 0));
        graph.onTouchEvent(MotionEvent.obtain(0, 1, MotionEvent.ACTION_MOVE, graph.toPixelX(toX), pixelY, 0));
        graph.onTouchEvent(MotionEvent.obtain(0, 2, MotionEvent.ACTION_UP, graph.toPixelX(toX), pixelY, 0));
    }

    private Bitmap render(GraphView graph) {
        Bitmap bitmap = Bitmap.createBitmap(graph.getWidth(), graph.getHeight(), Bitmap.Config.ARGB_8888);
        graph.draw(new Canvas(bitmap));
        return bitmap;
    }

    @Test
    public void tappingACurve_putsAReadoutOnIt() {
        GraphView graph = newGraph();
        GraphView.Graph line = line(1, Color.RED);
        graph.addGraph(line);

        tap(graph, 2, 2);

        assertSame(line, graph.getInspectedGraph());
        assertFalse("tapping the curve itself should read off a point, not shade the area",
                graph.isInspectingArea());
        assertEquals(2, graph.getInspectedX(), 0.2f);
    }

    @Test
    public void tappingBetweenACurveAndTheAxis_shadesTheArea() {
        GraphView graph = newGraph();
        GraphView.Graph line = line(1, Color.RED);
        graph.addGraph(line);

        // y = x is at 4 here, so 1 is well below the curve and above the axis.
        tap(graph, 4, 1);

        assertSame(line, graph.getInspectedGraph());
        assertTrue(graph.isInspectingArea());
    }

    @Test
    public void tappingNowhereNearACurve_takesTheReadoutOff() {
        GraphView graph = newGraph();
        graph.addGraph(line(1, Color.RED));
        tap(graph, 2, 2);
        assertNotNull(graph.getInspectedGraph());

        // Above y = x, so neither on the curve nor between it and the axis.
        tap(graph, 1, 5);

        assertNull(graph.getInspectedGraph());
    }

    @Test
    public void draggingTheCircle_movesItAlongTheCurve() {
        GraphView graph = newGraph();
        graph.addGraph(line(1, Color.RED));
        tap(graph, 2, 2);

        drag(graph, 2, 2, 5);

        assertEquals(5, graph.getInspectedX(), 0.2f);
    }

    /** Dragging the circle reads along the curve. It must not take the whole graph with it. */
    @Test
    public void draggingTheCircle_leavesTheGraphWhereItIs() {
        GraphView graph = newGraph();
        graph.addGraph(line(1, Color.RED));
        tap(graph, 2, 2);
        float leftEdge = graph.toGraphX(0);

        drag(graph, 2, 2, 5);

        assertEquals("the graph panned while the circle was being dragged",
                leftEdge, graph.toGraphX(0), 0.001f);
    }

    /** Away from the circle, a drag is still a pan. */
    @Test
    public void draggingElsewhere_stillPansTheGraph() {
        GraphView graph = newGraph();
        graph.addGraph(line(1, Color.RED));
        float leftEdge = graph.toGraphX(0);

        drag(graph, 1, 5, 4);

        assertTrue("dragging away from the circle should still pan",
                graph.toGraphX(0) != leftEdge);
    }

    /**
     * A pan that happens to start and end over a curve is still a pan. Dragging along y = x keeps
     * the finger on the curve the whole way, so only the travel tells the two apart.
     */
    @Test
    public void panningAlongACurve_doesNotLeaveAReadoutOnIt() {
        GraphView graph = newGraph();
        graph.addGraph(line(1, Color.RED));
        float pixelX = graph.toPixelX(2);
        float pixelY = graph.toPixelY(2);

        graph.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, pixelX, pixelY, 0));
        graph.onTouchEvent(MotionEvent.obtain(0, 1, MotionEvent.ACTION_MOVE, pixelX + 40, pixelY - 40, 0));
        graph.onTouchEvent(MotionEvent.obtain(0, 2, MotionEvent.ACTION_UP, pixelX + 40, pixelY - 40, 0));

        assertNull("a pan along a curve was taken for a tap on it", graph.getInspectedGraph());
    }

    @Test
    public void withoutAskingForIt_tappingDoesNothing() {
        GraphView graph = newGraph();
        graph.setInspectionEnabled(false);
        graph.addGraph(line(1, Color.RED));

        tap(graph, 2, 2);

        assertNull(graph.getInspectedGraph());
    }

    @Test
    public void turningInspectionOff_takesTheReadoutOff() {
        GraphView graph = newGraph();
        graph.addGraph(line(1, Color.RED));
        tap(graph, 2, 2);

        graph.setInspectionEnabled(false);

        assertNull(graph.getInspectedGraph());
    }

    @Test
    public void clearingTheGraphs_takesTheReadoutOff() {
        GraphView graph = newGraph();
        graph.addGraph(line(1, Color.RED));
        tap(graph, 2, 2);

        graph.clearGraphs();

        assertNull(graph.getInspectedGraph());
    }

    @Test
    public void theShadedArea_isDrawnUnderTheCurve() {
        GraphView graph = newGraph();
        graph.addGraph(line(1, Color.RED));

        tap(graph, 4, 1);
        Bitmap bitmap = render(graph);

        // Halfway between the axis and the curve at x = 3 is shaded.
        int shaded = bitmap.getPixel((int) graph.toPixelX(3), (int) graph.toPixelY(1.5f));
        assertTrue("the area under the curve was not shaded, found "
                + Integer.toHexString(shaded), Color.red(shaded) > Color.blue(shaded) + 16);

        // Above the curve is not.
        int clear = bitmap.getPixel((int) graph.toPixelX(1), (int) graph.toPixelY(5));
        assertEquals("the shading spilled out above the curve",
                Color.red(clear), Color.blue(clear));
    }

    @Test
    public void theCircle_isDrawnOnTheCurve() {
        GraphView graph = newGraph();
        graph.addGraph(line(1, Color.BLUE));

        tap(graph, 2, 2);
        Bitmap bitmap = render(graph);

        // The ring is hollow, so the middle shows the background through it.
        int middle = bitmap.getPixel((int) graph.toPixelX(2), (int) graph.toPixelY(2));
        assertEquals("the circle should be a ring, not a dot", Color.WHITE, middle);

        // And the ring itself sits a few pixels out, in the graph's colour.
        int ring = bitmap.getPixel((int) graph.toPixelX(2), (int) graph.toPixelY(2) - 6);
        assertTrue("the ring was not drawn in the graph's colour, found "
                + Integer.toHexString(ring), Color.blue(ring) > Color.red(ring) + 16);
    }

    @Test
    public void theValueOnACurve_isReadBetweenTheSampledPoints() {
        GraphView.Graph line = line(2, Color.RED);

        assertEquals(3f, GraphView.valueAt(line, 1.5f), 0.001f);
        assertEquals(-9f, GraphView.valueAt(line, -4.5f), 0.001f);
        assertNull("the graph does not reach this far", GraphView.valueAt(line, 40f));
    }

    @Test
    public void theSlopeOfACurve_isMeasuredAcrossTheStepAroundIt() {
        assertEquals(2f, GraphView.slopeAt(line(2, Color.RED), 1.5f), 0.01f);
        assertEquals(-3f, GraphView.slopeAt(line(-3, Color.RED), 1.5f), 0.01f);
    }

    @Test
    public void theAreaUnderACurve_isTheIntegralAcrossTheWindow() {
        GraphView.Graph line = line(1, Color.RED);

        // The triangle under y = x from 0 to 2.
        assertEquals(2f, GraphView.areaUnder(line, 0, 2), 0.001f);
        // Below the axis it counts as negative, so a symmetric window cancels out.
        assertEquals(0f, GraphView.areaUnder(line, -3, 3), 0.001f);
        assertEquals(-4.5f, GraphView.areaUnder(line, -3, 0), 0.001f);
    }
}
