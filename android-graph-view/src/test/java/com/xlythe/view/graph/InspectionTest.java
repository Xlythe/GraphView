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

import java.text.DecimalFormat;
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

    /** y = height - x², a hump crossing the axis at the square roots of the height. */
    private static GraphView.Graph hump(float height, int color) {
        List<Point> data = new ArrayList<>();
        for (int step = -100; step <= 100; step++) {
            float x = step / 10f;
            data.add(new Point(x, height - x * x));
        }
        return new GraphView.Graph("Y=" + height + "-X^2", color, data);
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

    /**
     * Once an area is shaded, tapping its curve cuts the area short there instead of swapping to a
     * point readout. Two taps give an area with both ends chosen, so even a line that runs off the
     * screen has an area worth reading.
     */
    @Test
    public void tappingTheCurveOfAShadedArea_cutsTheAreaShort() {
        GraphView graph = newGraph();
        GraphView.Graph line = line(1, Color.RED);
        graph.addGraph(line);
        tap(graph, 4, 1);
        assertTrue(graph.isInspectingArea());

        tap(graph, 2, 2);

        assertTrue("the tap should have cut the area, not swapped to a point",
                graph.isInspectingArea());
        assertEquals(1, graph.getAreaBoundCount());
        assertEquals(2, graph.getAreaBound(0), 0.2f);

        tap(graph, 6, 6);

        assertEquals(2, graph.getAreaBoundCount());
        assertEquals(6, graph.getAreaBound(1), 0.2f);
    }

    @Test
    public void aThirdTapOnTheCurve_startsTheBoundsOverAgain() {
        GraphView graph = newGraph();
        graph.addGraph(line(1, Color.RED));
        tap(graph, 4, 1);
        tap(graph, 2, 2);
        tap(graph, 6, 6);

        tap(graph, 3, 3);

        assertEquals(1, graph.getAreaBoundCount());
        assertEquals(3, graph.getAreaBound(0), 0.2f);
    }

    @Test
    public void draggingAnEndOfTheArea_movesThatEnd() {
        GraphView graph = newGraph();
        graph.addGraph(line(1, Color.RED));
        tap(graph, 4, 1);
        tap(graph, 2, 2);
        tap(graph, 6, 6);

        // Grab the second end, down at the axis rather than up on the curve.
        drag(graph, 6, 0, 5);

        assertEquals(2, graph.getAreaBoundCount());
        assertEquals(5, graph.getAreaBound(1), 0.2f);
        assertEquals("the other end should not have moved", 2, graph.getAreaBound(0), 0.2f);
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

    /** The slope is drawn as the line it describes, not only written out. */
    @Test
    public void theSlope_isDrawnAsALineThroughThePoint() {
        GraphView graph = newGraph();
        GraphView.Graph hump = hump(4, Color.BLUE);
        graph.addGraph(hump);

        tap(graph, 1, 3);
        Bitmap bitmap = render(graph);

        // Two units back along the slope the readout gives, well clear of the curve itself.
        float x = graph.getInspectedX();
        float tangent = GraphView.valueAt(hump, x) - 2 * GraphView.slopeAt(hump, x);
        int onTangent = bitmap.getPixel((int) graph.toPixelX(x - 2), (int) graph.toPixelY(tangent));
        assertTrue("no tangent line where the slope says one should be, found "
                + Integer.toHexString(onTangent), Color.blue(onTangent) > Color.red(onTangent) + 16);

        int offTangent = bitmap.getPixel(
                (int) graph.toPixelX(x - 2), (int) graph.toPixelY(tangent + 1.5f));
        assertEquals("the tangent was drawn at the wrong slope",
                Color.red(offTangent), Color.blue(offTangent));
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

    /**
     * Measured forward from the point, a curving graph reads its slope a step ahead of where it
     * was asked: on 4 - x squared at x = 1 it would say -1.9 rather than -2.
     */
    @Test
    public void theSlopeOfABendingCurve_isNotReadAStepAhead() {
        GraphView.Graph hump = hump(4, Color.RED);

        assertEquals(-2f, GraphView.slopeAt(hump, 1), 0.001f);
        assertEquals(2f, GraphView.slopeAt(hump, -1), 0.001f);
        assertEquals("the top of the hump is flat", 0f, GraphView.slopeAt(hump, 0), 0.001f);
    }

    /** The area is closed off by where the curve crosses the axis, not by the edge of the screen. */
    @Test
    public void theAreaUnderAHump_isBoundedByWhereTheCurveCrossesTheAxis() {
        GraphView.Graph hump = hump(1, Color.RED);

        GraphView.Area area = GraphView.areaAround(hump, 0);

        assertNotNull(area);
        assertTrue("the hump closes at both ends, so its area is a number", area.closed);
        assertEquals(-1, area.from, 0.01f);
        assertEquals(1, area.to, 0.01f);
        // The integral of 1 - x^2 from -1 to 1.
        assertEquals(4f / 3f, area.value, 0.01f);
        assertFalse("a closed area should read as a number",
                area.describe(new DecimalFormat("#.###")).contains("∞"));
    }

    /** Below the axis the hump never closes, so that side runs off to negative infinity. */
    @Test
    public void theAreaOutsideAHump_runsOffToInfinity() {
        GraphView.Graph hump = hump(1, Color.RED);

        GraphView.Area area = GraphView.areaAround(hump, 5);

        assertNotNull(area);
        assertFalse("nothing closes this side off, so it has no area", area.closed);
        assertEquals("-∞", area.describe(new DecimalFormat("#.###")));
    }

    /** A line that leaves the top of the screen and never comes back has no area either. */
    @Test
    public void theAreaUnderALineThatKeepsClimbing_isInfinite() {
        GraphView.Area area = GraphView.areaAround(line(1, Color.RED), 5);

        assertNotNull(area);
        assertFalse(area.closed);
        assertEquals("∞", area.describe(new DecimalFormat("#.###")));
        assertEquals("it should still start where the line crossed the axis", 0, area.from, 0.01f);
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
