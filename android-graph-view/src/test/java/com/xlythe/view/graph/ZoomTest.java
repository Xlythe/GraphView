package com.xlythe.view.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;

/**
 * At mdpi a dp is a pixel, so a grid line starts out 25px apart and worth 1, which makes a pixel
 * worth 1/25 of the graph.
 */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "mdpi")
public class ZoomTest {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 400;

    private GraphView newGraph() {
        GraphView graph = new GraphView(ApplicationProvider.getApplicationContext());
        graph.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        graph.layout(0, 0, WIDTH, HEIGHT);
        return graph;
    }

    /** How many pixels one unit of the graph takes up. Bigger means zoomed further in. */
    private static float pixelsPerUnit(GraphView graph) {
        return graph.toPixelX(1) - graph.toPixelX(0);
    }

    private static MotionEvent pinch(int action, float left, float right) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
        for (int i = 0; i < 2; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].y = HEIGHT / 2f;
        }
        coords[0].x = left;
        coords[1].x = right;
        return MotionEvent.obtain(0, 0, action, 2, properties, coords, 0, 0, 1, 1, 0, 0, 0, 0);
    }

    /** Puts two fingers down {@code from} apart and spreads or squeezes them to {@code to} apart. */
    private static void pinchFrom(GraphView graph, float from, float to) {
        float centre = WIDTH / 2f;
        graph.onTouchEvent(pinch(MotionEvent.ACTION_DOWN, centre - from / 2, centre + from / 2));
        graph.onTouchEvent(pinch(MotionEvent.ACTION_MOVE, centre - to / 2, centre + to / 2));
        graph.onTouchEvent(pinch(MotionEvent.ACTION_UP, centre - to / 2, centre + to / 2));
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
    }

    @Test
    public void spreadingTheFingers_zoomsIn() {
        GraphView graph = newGraph();
        float before = pixelsPerUnit(graph);

        pinchFrom(graph, 100, 400);

        assertTrue("spreading the fingers should zoom in, but a unit went from "
                + before + "px to " + pixelsPerUnit(graph) + "px", pixelsPerUnit(graph) > before);
    }

    @Test
    public void squeezingTheFingers_zoomsOut() {
        GraphView graph = newGraph();
        float before = pixelsPerUnit(graph);

        pinchFrom(graph, 400, 100);

        assertTrue("squeezing the fingers should zoom out, but a unit went from "
                + before + "px to " + pixelsPerUnit(graph) + "px", pixelsPerUnit(graph) < before);
    }

    /**
     * Zoom used to be worked out by subtracting how far the fingers had moved, rather than by how
     * much further apart they were. Spread them far enough and the graph was scaled by a negative
     * number, which turned it inside out, and at exactly the wrong distance it divided by zero.
     */
    @Test
    public void spreadingTheFingersFar_doesNotTurnTheGraphInsideOut() {
        GraphView graph = newGraph();

        pinchFrom(graph, 20, 380);

        assertTrue("a grid line ended up worth " + graph.getZoomLevel(), graph.getZoomLevel() > 0);
        assertTrue("a unit ended up " + pixelsPerUnit(graph) + "px wide", pixelsPerUnit(graph) > 0);
        assertTrue(Float.isFinite(pixelsPerUnit(graph)));
    }

    @Test
    public void pinching_scalesByHowMuchFurtherApartTheFingersAre() {
        GraphView graph = newGraph();
        float before = pixelsPerUnit(graph);

        pinchFrom(graph, 100, 400);

        assertEquals(4 * before, pixelsPerUnit(graph), 0.5f);
    }

    @Test
    public void pinching_leavesWhatIsBetweenTheFingersWhereItWas() {
        GraphView graph = newGraph();
        float centre = WIDTH / 2f;
        float under = graph.toGraphX(centre);

        pinchFrom(graph, 100, 330);

        assertEquals(under, graph.toGraphX(centre), 0.05f);
    }

    @Test
    public void theNumbersOnTheGrid_stayRound_howeverFarYouPinch() {
        for (int spread = 60; spread <= 390; spread += 7) {
            GraphView graph = newGraph();
            pinchFrom(graph, 200, spread);

            double level = graph.getZoomLevel();
            double mantissa = level / Math.pow(10, Math.floor(Math.log10(level)));
            assertTrue("a grid line came out worth " + level + " after pinching to " + spread,
                    isClose(mantissa, 1) || isClose(mantissa, 2) || isClose(mantissa, 5));
        }
    }

    private static boolean isClose(double a, double b) {
        return Math.abs(a - b) < 1e-4;
    }

    @Test
    public void zoomingIn_stepsToTheNextRoundNumberDown() {
        GraphView graph = newGraph();
        assertEquals(1f, graph.getZoomLevel(), 0);

        graph.zoomIn();
        settle();
        assertEquals(0.5f, graph.getZoomLevel(), 0);

        graph.zoomIn();
        settle();
        assertEquals(0.2f, graph.getZoomLevel(), 0);
    }

    @Test
    public void zoomingOut_stepsToTheNextRoundNumberUp() {
        GraphView graph = newGraph();

        graph.zoomOut();
        settle();
        assertEquals(2f, graph.getZoomLevel(), 0);

        graph.zoomOut();
        settle();
        assertEquals(5f, graph.getZoomLevel(), 0);
    }

    /** The zoom buttons glide rather than jump, so you can see where the graph went. */
    @Test
    public void zooming_glidesRatherThanJumping() {
        GraphView graph = newGraph();
        float start = pixelsPerUnit(graph);

        graph.zoomIn();

        assertEquals("the zoom arrived before a single frame had been drawn, so it jumped",
                start, pixelsPerUnit(graph), 0.001f);
        settle();
        assertEquals(2 * start, pixelsPerUnit(graph), 0.001f);
    }

    /**
     * Partway between two steps the grid keeps the round number it had and slides its lines apart
     * to cover the difference. That sliding, and the spring back when the number does step, is what
     * reads as the grid snapping.
     */
    @Test
    public void betweenSteps_theSpacingTakesUpTheDifference() {
        GraphView graph = newGraph();

        pinchFrom(graph, 200, 260);

        assertEquals("a grid line should still be worth a round 1", 1f, graph.getZoomLevel(), 0);
        assertEquals("but the lines should have slid apart to cover the zoom",
                32.5f, pixelsPerUnit(graph), 1f);
    }

    @Test
    public void resettingTheZoom_putsTheGridBackToOne() {
        GraphView graph = newGraph();
        pinchFrom(graph, 100, 380);

        graph.zoomReset();

        assertEquals(1f, graph.getZoomLevel(), 0);
        assertEquals(25f, pixelsPerUnit(graph), 0.001f);
    }

    @Test
    public void panning_doesNotDriftAsItIsRepeated() {
        GraphView graph = newGraph();
        float before = graph.toGraphX(0);

        for (int i = 0; i < 200; i++) {
            graph.panBy(7, 0);
        }
        for (int i = 0; i < 200; i++) {
            graph.panBy(-7, 0);
        }

        assertEquals(before, graph.toGraphX(0), 0.001f);
    }

    @Test
    public void roundNumbers_areOneTwoOrFiveTimesAPowerOfTen() {
        assertEquals(1, GraphView.snapToNiceNumber(1.1), 1e-9);
        assertEquals(2, GraphView.snapToNiceNumber(1.9), 1e-9);
        assertEquals(5, GraphView.snapToNiceNumber(4), 1e-9);
        assertEquals(10, GraphView.snapToNiceNumber(8), 1e-9);
        assertEquals(0.2, GraphView.snapToNiceNumber(0.23), 1e-9);
        assertEquals(500, GraphView.snapToNiceNumber(480), 1e-9);
        assertEquals(1, GraphView.snapToNiceNumber(0), 1e-9);
        assertEquals(1, GraphView.snapToNiceNumber(Double.NaN), 1e-9);
    }

    @Test
    public void steppingBetweenRoundNumbers_walksOneTwoFive() {
        assertEquals(2, GraphView.nextNiceNumber(1, true), 1e-6);
        assertEquals(5, GraphView.nextNiceNumber(2, true), 1e-6);
        assertEquals(10, GraphView.nextNiceNumber(5, true), 1e-6);
        assertEquals(0.5, GraphView.nextNiceNumber(1, false), 1e-6);
        assertEquals(0.2, GraphView.nextNiceNumber(0.5, false), 1e-6);
        assertEquals(0.1, GraphView.nextNiceNumber(0.2, false), 1e-6);
    }
}
