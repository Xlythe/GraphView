package com.xlythe.view.graph;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import com.xlythe.math.Point;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;

@SuppressWarnings({"FieldCanBeLocal", "SameParameterValue", "WeakerAccess", "unused"})
public class GraphView extends View {
    private static final boolean DEBUG = false;

    private static final int LINES = 1;
    private static final int DOTS = 2;
    private static final int CURVES = 3;

    private static final int DRAG = 1;
    private static final int ZOOM = 2;

    /** How long the zoom buttons take to glide to the next step. */
    private static final long ZOOM_DURATION = 250;

    /** The numbers a grid line is allowed to stand for, give or take a power of ten. */
    private static final double[] NICE_NUMBERS = {0.5, 1, 2, 5, 10};

    private final List<PanListener> mPanListeners = new ArrayList<>();
    private final List<ZoomListener> mZoomListeners = new ArrayList<>();

    private final Rect mTempRect = new Rect();
    private final int mDrawingAlgorithm = LINES;
    private final DecimalFormat mFormat = new DecimalFormat("#.#");

    private int mGridWidth;
    private int mAxisWidth;
    private int mGraphWidth;
    private int mBorderWidth;

    private Paint mBackgroundPaint;
    private Paint mTextPaint;
    private Paint mGridPaint;
    @Nullable
    private Paint mAxisPaint;
    private Paint mGraphPaint;
    private Paint mDebugPaint;

    private int mOffsetX;
    private int mOffsetY;
    private int mLineMargin;
    private int mBaseLineMargin;
    private int mMinLineMargin;
    private int mTextPaintSize;
    private int mTextMargin;
    private float mZoomLevel = 1;
    private List<Graph> mData;

    private float mStartX;
    private float mStartY;
    private int mDragOffsetX;
    private int mDragOffsetY;
    private int mDragRemainderX;
    private int mDragRemainderY;

    private int mRemainderX;
    private int mRemainderY;
    private double mZoomInitDistance;
    private float mZoomInitUnitsPerPixel;
    @Nullable
    private ValueAnimator mZoomAnimator;
    private int mMode;
    private int mPointers;
    private boolean mShowGrid = true;
    private boolean mShowAxis = true;
    private boolean mShowOutline = true;
    private boolean mPanEnabled = true;
    private boolean mZoomEnabled = true;
    private boolean mInlineNumbers = false;

    private boolean mInspectionEnabled = false;
    @Nullable
    private Graph mInspectedGraph;
    /** Where along the inspected graph the readout sits, in the graph's own units. */
    private float mInspectedX;
    private boolean mInspectingArea;
    /** Where the shaded area has been cut short, in the graph's units. Up to two, in tap order. */
    private final float[] mAreaBounds = new float[2];
    private int mAreaBoundCount;
    private boolean mDraggingInspection;
    /** Which of the two bounds the finger has hold of, or -1 for the point on the curve. */
    private int mDraggingBound = -1;
    private Paint mInspectionPaint;
    private Paint mInspectionTextPaint;
    private int mInspectionRadius;
    private int mReadoutBorderWidth;
    private int mSlopeLabelOffset;
    private final Path mAreaPath = new Path();
    private final DecimalFormat mReadoutFormat = new DecimalFormat("#.###");

    private float mDownX;
    private float mDownY;
    private boolean mMovedSinceDown;
    private int mTouchSlop;

    private boolean mGraphIsCentered = true;
    private OnCenterListener mOnCenterListener;
    private List<Point> curveCachedData;
    private List<Point> curveCachedMutatedData;

    public GraphView(Context context) {
        super(context);
        setup(context, null);
    }

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup(context, attrs);
    }

    public GraphView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public GraphView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setup(context, attrs);
    }

    private void setup(Context context, AttributeSet attrs) {
        mGridWidth = fromDp(1);
        mAxisWidth = fromDp(3);
        mGraphWidth = fromDp(3);
        mBorderWidth = fromDp(3);

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(Color.WHITE);
        mBackgroundPaint.setStyle(Style.FILL);

        mTextMargin = fromDp(3);
        // The numbers along the edges live in the band before the first grid line, and that band is
        // a fixed width. They follow the reader's font size down, and up until they would not fit,
        // past which they run into each other and out over the graph.
        mTextPaintSize = Math.min(fromSp(16), fromDp(25) - 2 * mTextMargin);
        mTextPaint = new Paint();
        mTextPaint.setColor(Color.BLACK);
        mTextPaint.setTextSize(mTextPaintSize);

        mGridPaint = new Paint();
        mGridPaint.setColor(Color.LTGRAY);
        mGridPaint.setStyle(Style.STROKE);
        mGridPaint.setStrokeWidth(mGridWidth);

        mGraphPaint = new Paint();
        mGraphPaint.setColor(Color.CYAN);
        mGraphPaint.setStyle(Style.STROKE);
        mGraphPaint.setStrokeWidth(mGraphWidth);

        mDebugPaint = new Paint();
        mDebugPaint.setColor(Color.MAGENTA);
        mDebugPaint.setStyle(Style.STROKE);
        mDebugPaint.setStrokeWidth(mGraphWidth);

        mInspectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mInspectionPaint.setStyle(Style.FILL);

        mInspectionTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mInspectionTextPaint.setColor(0xde000000);
        mInspectionTextPaint.setTextSize(fromSp(14));

        mInspectionRadius = fromDp(7);
        mReadoutBorderWidth = fromDp(2);
        mSlopeLabelOffset = fromDp(72);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mLineMargin = mBaseLineMargin = fromDp(25);
        // Grid lines stretch to about a third again as far apart as the base before the numbers on
        // them snap to the next step and the spacing springs back. Leave room for that, or lines
        // would start dropping out halfway through a pinch.
        mMinLineMargin = fromDp(15);

        zoomReset();

        mData = new ArrayList<>();

        if (attrs != null) {
            final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GraphView, 0, 0);
            setShowGrid(a.getBoolean(R.styleable.GraphView_showGrid, mShowGrid));
            setShowInlineNumbers(a.getBoolean(R.styleable.GraphView_showInlineNumbers, mInlineNumbers));
            setShowOutline(a.getBoolean(R.styleable.GraphView_showOutline, mShowOutline));
            setPanEnabled(a.getBoolean(R.styleable.GraphView_panEnabled, mPanEnabled));
            setZoomEnabled(a.getBoolean(R.styleable.GraphView_zoomEnabled, mZoomEnabled));
            setBackgroundColor(a.getColor(R.styleable.GraphView_graphBackgroundColor, mBackgroundPaint.getColor()));
            setGridColor(a.getColor(R.styleable.GraphView_gridColor, mGridPaint.getColor()));
            if (a.hasValue(R.styleable.GraphView_axisColor)) {
                setAxisColor(a.getColor(R.styleable.GraphView_axisColor, mGridPaint.getColor()));
            }
            setTextColor(a.getColor(R.styleable.GraphView_numberTextColor, mTextPaint.getColor()));
            a.recycle();
        }
    }

    private int fromDp(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private int fromSp(int sp) {
        // Scaled pixels, not density pixels: text here follows the font size the reader chose, the
        // same as text anywhere else. Measuring it in dp quietly ignored that setting.
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    public void zoomReset() {
        setZoomLevel(1);

        // Zero everything out
        mRemainderX = mRemainderY = mOffsetX = mOffsetY = 0;

        mGraphIsCentered = true;

        onSizeChanged(getWidth(), getHeight(), 0, 0);
        postInvalidate();

        for (PanListener listener : mPanListeners) {
            listener.panApplied();
        }
        for (ZoomListener listener : mZoomListeners) {
            listener.zoomApplied(mZoomLevel);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mPanEnabled && !mZoomEnabled && !mInspectionEnabled) {
            return super.onTouchEvent(event);
        }

        // Update mode if pointer count changes
        if (mPointers != event.getPointerCount()) {
            setMode(event);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                setMode(event);
                mDownX = event.getX();
                mDownY = event.getY();
                mMovedSinceDown = false;
                mDraggingInspection = mInspectionEnabled && isOnTheReadout(mDownX, mDownY);
                break;
            case MotionEvent.ACTION_UP:
                if (mMode == ZOOM) {
                    // The curves rescaled with the pinch but were never redrawn for the new
                    // viewport. Now that the fingers are up, it is worth the work.
                    notifyZoomed();
                }
                if (mInspectionEnabled && !mMovedSinceDown && !mDraggingInspection) {
                    inspectAt(mDownX, mDownY);
                }
                mDraggingInspection = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mMovedSinceDown
                        && Math.hypot(event.getX() - mDownX, event.getY() - mDownY) > mTouchSlop) {
                    mMovedSinceDown = true;
                }
                if (mDraggingInspection) {
                    dragReadoutTo(event.getX());
                } else if (mMode == DRAG && mPanEnabled) {
                    float deltaX = event.getX() - mStartX;
                    float deltaY = event.getY() - mStartY;

                    // Cancel out the previous drag
                    mOffsetX += mDragOffsetX;
                    mOffsetY += mDragOffsetY;
                    mRemainderX -= mDragRemainderX;
                    mRemainderY -= mDragRemainderY;

                    // Calculate new drag
                    mDragOffsetX = (int) (deltaX / mLineMargin);
                    mDragOffsetY = (int) (deltaY / mLineMargin);
                    mDragRemainderX = (int) (deltaX) % mLineMargin;
                    mDragRemainderY = (int) (deltaY) % mLineMargin;

                    // Apply new drag
                    mOffsetX -= mDragOffsetX;
                    mOffsetY -= mDragOffsetY;
                    mRemainderX += mDragRemainderX;
                    mRemainderY += mDragRemainderY;

                    // Because we're summing the remainders, we can go above % line margin
                    mOffsetX -= mRemainderX / mLineMargin;
                    mRemainderX %= mLineMargin;
                    mOffsetY -= mRemainderY / mLineMargin;
                    mRemainderY %= mLineMargin;

                    // Notify listeners
                    for (PanListener listener : mPanListeners) {
                        listener.panApplied();
                    }
                    mGraphIsCentered = false;
                } else if (mMode == ZOOM && mZoomEnabled) {
                    double distance = getDistance(new Point(event.getX(0), event.getY(0)), new Point(event.getX(1), event.getY(1)));
                    if (distance > 0 && mZoomInitDistance > 0) {
                        // Spreading the fingers apart leaves a pixel worth less of the graph, by
                        // however much further apart they are than where they started.
                        zoomTo((float) (mZoomInitUnitsPerPixel * mZoomInitDistance / distance),
                                (event.getX(0) + event.getX(1)) / 2,
                                (event.getY(0) + event.getY(1)) / 2);
                    }
                }
                break;
        }
        invalidate();
        return true;
    }

    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld) {
        super.onSizeChanged(xNew, yNew, xOld, yOld);

        // If the graph was centered, recenter it. If it was panned, leave it alone.
        if (mGraphIsCentered) {
            // Lets calculate the min x and y values
            mOffsetX = (-xNew / mLineMargin) / 2;
            mOffsetY = (-yNew / mLineMargin) / 2;

            // But! The view will probably not perfectly match up to line margins.
            // So put half the remainder on top and half on bottom
            mRemainderX = (xNew % mLineMargin) / 2;
            mRemainderY = (yNew % mLineMargin) / 2;

            // Unfortunately, there's one gotcha left. An even number of line margins won't center
            // the axis! So push everything down by half a line margin, to center on the axis.
            if ((xNew / mLineMargin) % 2 == 1) {
                mRemainderX += mLineMargin / 2;
            }
            if ((yNew / mLineMargin) % 2 == 1) {
                mRemainderY += mLineMargin / 2;
            }

            if (mOnCenterListener != null) {
                mOnCenterListener.onCentered();
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        canvas.drawPaint(mBackgroundPaint);

        // Draw bounding box
        mGridPaint.setStrokeWidth(mBorderWidth);
        if (mShowOutline) {
            canvas.drawRect(mLineMargin, mLineMargin,
                    getWidth() - mBorderWidth / 2, getHeight() - mBorderWidth / 2, mGridPaint);
        }

        // Draw the grid lines
        Rect bounds = mTempRect;
        int previousLine = 0;
        boolean inlineNumbersDrawn = !mInlineNumbers;
        Paint axisPaint = mAxisPaint == null ? mGridPaint : mAxisPaint;
        for (int i = mInlineNumbers ? 0 : 1, j = mOffsetX; i * mLineMargin < getWidth(); i++, j++) {
            // Draw vertical lines
            int x = i * mLineMargin + mRemainderX;
            if (!mInlineNumbers && (x < mLineMargin || x - previousLine < mMinLineMargin)) continue;
            previousLine = x;

            if (j == 0 && mShowAxis) {
                axisPaint.setStrokeWidth(mAxisWidth);
                canvas.drawLine(x, mInlineNumbers ? 0 : mLineMargin, x, getHeight(), axisPaint);
            } else if (mShowGrid) {
                mGridPaint.setStrokeWidth(mGridWidth);
                canvas.drawLine(x, mInlineNumbers ? 0 : mLineMargin, x, getHeight(), mGridPaint);
            }

            if (!mInlineNumbers) {
                // Draw label on top
                String text = mFormat.format(j * mZoomLevel);
                int textLength = ((text.startsWith("-") ? text.length() - 1 : text.length()) + 1) / 2;
                mTextPaint.setTextSize(mTextPaintSize / textLength);
                mTextPaint.getTextBounds(text, 0, text.length(), bounds);
                int textWidth = bounds.right - bounds.left;
                canvas.drawText(text, x - textWidth / 2, mLineMargin / 2 + mTextPaint.getTextSize() / 2, mTextPaint);
            } else if (j + 1 == 0) {
                // Draw the y min
                String text = mFormat.format(getYAxisMin());
                mTextPaint.getTextBounds(text, 0, text.length(), bounds);
                int textWidth = bounds.right - bounds.left;
                int xCord = x - textWidth;
                xCord = Math.min(getWidth() - 2 * mLineMargin, xCord);
                xCord = Math.max(2 * mLineMargin - textWidth, xCord);
                xCord = Math.max(mTextMargin, xCord); // Don't let the text go off the screen. Margin of mTextMargin
                xCord = Math.min(getWidth() - textWidth - mTextMargin, xCord); // Don't let the text go off the screen.
                canvas.drawText(text, xCord, getHeight() - mLineMargin + mTextPaintSize, mTextPaint);

                // Draw the y max
                text = mFormat.format(getYAxisMax());
                mTextPaint.getTextBounds(text, 0, text.length(), bounds);
                textWidth = bounds.right - bounds.left;
                xCord = x - textWidth;
                xCord = Math.min(getWidth() - 2 * mLineMargin, xCord);
                xCord = Math.max(2 * mLineMargin - textWidth, xCord);
                xCord = Math.max(mTextMargin, xCord); // Don't let the text go off the screen. Margin of mTextPaintSize
                xCord = Math.min(getWidth() - textWidth - mTextMargin, xCord); // Don't let the text go off the screen.
                canvas.drawText(text, xCord, mLineMargin, mTextPaint);

                inlineNumbersDrawn = true;
            }
        }
        if (!inlineNumbersDrawn) {
            boolean drawOnRightSide = getXAxisMin() + (getXAxisMax() - getXAxisMin()) / 2 < 0;

            // Draw the y min
            String text = mFormat.format(getYAxisMin());
            mTextPaint.getTextBounds(text, 0, text.length(), bounds);
            int textWidth = bounds.right - bounds.left;
            int xCord;
            if (drawOnRightSide) {
                xCord = getWidth() - 2 * mLineMargin;
            } else {
                xCord = 2 * mLineMargin - textWidth;
            }
            xCord = Math.max(mTextMargin, xCord); // Don't let the text go off the screen. Margin of mTextMargin
            xCord = Math.min(getWidth() - textWidth - mTextMargin, xCord); // Don't let the text go off the screen.
            int yCord = getHeight() - mLineMargin + mTextPaintSize;
            canvas.drawText(text, xCord, yCord, mTextPaint);

            // Draw the y max
            text = mFormat.format(getYAxisMax());
            mTextPaint.getTextBounds(text, 0, text.length(), bounds);
            textWidth = bounds.right - bounds.left;
            if (drawOnRightSide) {
                xCord = getWidth() - 2 * mLineMargin;
            } else {
                xCord = 2 * mLineMargin - textWidth;
            }
            xCord = Math.max(mTextMargin, xCord); // Don't let the text go off the screen. Margin of mTextMargin
            xCord = Math.min(getWidth() - textWidth - mTextMargin, xCord); // Don't let the text go off the screen.
            yCord = mLineMargin;
            canvas.drawText(text, xCord, yCord, mTextPaint);
        }
        previousLine = 0;
        inlineNumbersDrawn = !mInlineNumbers;
        for (int i = mInlineNumbers ? 0 : 1, j = mOffsetY; i * mLineMargin < getHeight(); i++, j++) {
            // Draw horizontal lines
            int y = i * mLineMargin + mRemainderY;
            if (!mInlineNumbers && (y < mLineMargin || y - previousLine < mMinLineMargin)) continue;
            previousLine = y;

            if (j == 0 && mShowAxis) {
                axisPaint.setStrokeWidth(mAxisWidth);
                canvas.drawLine(mInlineNumbers ? 0 : mLineMargin, y, getWidth(), y, axisPaint);
            } else if (mShowGrid) {
                mGridPaint.setStrokeWidth(mGridWidth);
                canvas.drawLine(mInlineNumbers ? 0 : mLineMargin, y, getWidth(), y, mGridPaint);
            }

            if (!mInlineNumbers) {
                // Draw label on left
                String text = mFormat.format(-j * mZoomLevel);
                int textLength = ((text.startsWith("-") ? text.length() - 1 : text.length()) + 1) / 2;
                mTextPaint.setTextSize(mTextPaintSize / textLength);
                mTextPaint.getTextBounds(text, 0, text.length(), bounds);
                int textHeight = bounds.bottom - bounds.top;
                int textWidth = bounds.right - bounds.left;
                canvas.drawText(text, mLineMargin / 2 - textWidth / 2, y + textHeight / 2, mTextPaint);
            } else if (j - 1 == 0) {
                // Draw the x min
                String text = mFormat.format(getXAxisMin());
                mTextPaint.getTextBounds(text, 0, text.length(), bounds);
                int textWidth = bounds.right - bounds.left;
                int xCord = mLineMargin - textWidth;
                xCord = Math.max(mTextMargin, xCord); // Don't let the text go off the screen. Margin of mTextMargin
                xCord = Math.min(getWidth() - textWidth - mTextMargin, xCord); // Don't let the text go off the screen.
                int yCord = y;
                yCord = Math.min(getHeight() - 2 * mLineMargin + mTextPaintSize, yCord);
                yCord = Math.max(2 * mLineMargin, yCord);
                canvas.drawText(text, xCord, yCord, mTextPaint);

                // Draw the x max
                text = mFormat.format(getXAxisMax());
                mTextPaint.getTextBounds(text, 0, text.length(), bounds);
                textWidth = bounds.right - bounds.left;
                xCord = getWidth() - mLineMargin;
                xCord = Math.max(mTextMargin, xCord); // Don't let the text go off the screen. Margin of mTextMargin
                xCord = Math.min(getWidth() - textWidth - mTextMargin, xCord); // Don't let the text go off the screen.
                canvas.drawText(text, xCord, yCord, mTextPaint);

                inlineNumbersDrawn = true;
            }
        }
        if (!inlineNumbersDrawn) {
            boolean drawOnBottom = getYAxisMin() + (getYAxisMax() - getYAxisMin()) / 2 > 0;

            // Draw the x min
            String text = mFormat.format(getXAxisMin());
            mTextPaint.getTextBounds(text, 0, text.length(), bounds);
            int textWidth = bounds.right - bounds.left;
            int xCord = mLineMargin - textWidth;
            xCord = Math.max(mTextMargin, xCord); // Don't let the text go off the screen. Margin of mTextMargin
            xCord = Math.min(getWidth() - textWidth - mTextMargin, xCord); // Don't let the text go off the screen.
            int yCord;
            if (drawOnBottom) {
                yCord = getHeight() - 2 * mLineMargin + mTextPaintSize;
            } else {
                yCord = 2 * mLineMargin;
            }
            canvas.drawText(text, xCord, yCord, mTextPaint);

            // Draw the x max
            text = mFormat.format(getXAxisMax());
            mTextPaint.getTextBounds(text, 0, text.length(), bounds);
            textWidth = bounds.right - bounds.left;
            xCord = getWidth() - mLineMargin;
            xCord = Math.max(mTextMargin, xCord); // Don't let the text go off the screen. Margin of mTextMargin
            xCord = Math.min(getWidth() - textWidth - mTextMargin, xCord); // Don't let the text go off the screen.
            if (drawOnBottom) {
                yCord = getHeight() - 2 * mLineMargin + mTextPaintSize;
            } else {
                yCord = 2 * mLineMargin;
            }
            canvas.drawText(text, xCord, yCord, mTextPaint);
        }

        // Restrict drawing the graph to the grid
        if (!mInlineNumbers) {
            canvas.clipRect(mLineMargin, mLineMargin,
                    getWidth() - mBorderWidth, getHeight() - mBorderWidth);
        }

        // Create a path to draw smooth arcs
        for (Graph graph : mData) {
            if (graph.visible && graph.data.size() != 0) {
                mGraphPaint.setColor(graph.color);
                if (mDrawingAlgorithm == LINES) {
                    drawWithStraightLines(graph.data, canvas, mGraphPaint);
                } else if (mDrawingAlgorithm == DOTS) {
                    drawDots(graph.data, canvas, mGraphPaint);
                } else if (mDrawingAlgorithm == CURVES) {
                    drawWithCurves(graph.data, canvas, mGraphPaint);
                }
            }
        }

        drawInspection(canvas);

        if (DEBUG) {
            canvas.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2, mDebugPaint);
            canvas.drawLine(getWidth() / 2, 0, getWidth() / 2, getHeight(), mDebugPaint);
        }
    }

    private void drawWithStraightLines(List<Point> data, Canvas canvas, Paint paint) {
        Point previousPoint = null;
        for (Point currentPoint : data) {
            if (previousPoint == null) {
                previousPoint = currentPoint;
                continue;
            }

            int aX = getRawX(previousPoint);
            int aY = getRawY(previousPoint);
            int bX = getRawX(currentPoint);
            int bY = getRawY(currentPoint);

            previousPoint = currentPoint;

            if (tooFar(aX, aY, bX, bY)) continue;

            canvas.drawLine(aX, aY, bX, bY, paint);
        }
    }

    private void drawDots(List<Point> data, Canvas canvas, Paint paint) {
        for (Point p : data) {
            canvas.drawPoint(getRawX(p), getRawY(p), paint);
        }
    }

    private void drawWithCurves(List<Point> data, Canvas canvas, Paint paint) {
        if (curveCachedData == data) {
            drawWithStraightLines(curveCachedMutatedData, canvas, paint);
            return;
        }

        float tension = 0.5f;
        int numOfSegments = 16;
        List<Point> mutatedData = new ArrayList<>(data);
        List<Point> newData = new ArrayList<>(data.size());

        // The algorithm require a previous and next point to the actual point array.
        // Duplicate first points to beginning, end points to end
        mutatedData.add(0, mutatedData.get(0));
        mutatedData.add(mutatedData.get(mutatedData.size() - 1));


        // ok, lets start..

        // 1. loop goes through point array
        // 2. loop goes through each segment between the 2 pts + 1e point before and after
        for (int i = 1; i < data.size() - 2; i++) {
            for (int t = 0; t <= numOfSegments; t++) {

                // calc tension vectors
                float t1x = (data.get(i + 1).getX() - data.get(i - 1).getX()) * tension;
                float t2x = (data.get(i + 2).getX() - data.get(i).getX()) * tension;

                float t1y = (data.get(i + 1).getY() - data.get(i - 1).getY()) * tension;
                float t2y = (data.get(i + 2).getY() - data.get(i).getY()) * tension;

                // calc step
                float st = t / numOfSegments;

                // calc cardinals
                double c1 = 2 * Math.pow(st, 3) - 3 * Math.pow(st, 2) + 1;
                double c2 = -(2 * Math.pow(st, 3)) + 3 * Math.pow(st, 2);
                double c3 = Math.pow(st, 3) - 2 * Math.pow(st, 2) + st;
                double c4 = Math.pow(st, 3) - Math.pow(st, 2);

                // calc x and y cords with common control vectors
                float x = (float) (c1 * data.get(i).getX() + c2 * data.get(i + 1).getX() + c3 * t1x + c4 * t2x);
                float y = (float) (c1 * data.get(i).getY() + c2 * data.get(i + 1).getY() + c3 * t1y + c4 * t2y);

                //store points in array
                newData.add(new Point(x, y));

            }
        }

        curveCachedData = data;
        curveCachedMutatedData = newData;

        drawWithStraightLines(newData, canvas, paint);
    }

    private int getRawX(Point p) {
        if (p == null || Double.isNaN(p.getX()) || Double.isInfinite(p.getX())) return -1;
        return (int) toPixelX(p.getX());
    }

    private int getRawY(Point p) {
        if (p == null || Double.isNaN(p.getY()) || Double.isInfinite(p.getY())) return -1;
        return (int) toPixelY(p.getY());
    }

    /** Where on screen, left to right, the graph's {@code x} falls. */
    public float toPixelX(float x) {
        // The left line is at pos
        float leftLine = (mInlineNumbers ? 0 : mLineMargin) + mRemainderX;
        // And equals
        float val = mOffsetX * mZoomLevel;
        // And changes at a rate of
        float slope = mLineMargin / mZoomLevel;
        // Put it all together
        return slope * (x - val) + leftLine;
    }

    /** Where on screen, top to bottom, the graph's {@code y} falls. */
    public float toPixelY(float y) {
        // The top line is at pos
        float topLine = (mInlineNumbers ? 0 : mLineMargin) + mRemainderY;
        // And equals
        float val = -mOffsetY * mZoomLevel;
        // And changes at a rate of
        float slope = mLineMargin / mZoomLevel;
        // Put it all together
        return -slope * (y - val) + topLine;
    }

    /** The graph's x at this point on screen. The other way round from {@link #toPixelX}. */
    public float toGraphX(float pixelX) {
        float leftLine = (mInlineNumbers ? 0 : mLineMargin) + mRemainderX;
        float val = mOffsetX * mZoomLevel;
        float slope = mLineMargin / mZoomLevel;
        return (pixelX - leftLine) / slope + val;
    }

    /** The graph's y at this point on screen. The other way round from {@link #toPixelY}. */
    public float toGraphY(float pixelY) {
        float topLine = (mInlineNumbers ? 0 : mLineMargin) + mRemainderY;
        float val = -mOffsetY * mZoomLevel;
        float slope = mLineMargin / mZoomLevel;
        return -(pixelY - topLine) / slope + val;
    }

    private boolean tooFar(float aX, float aY, float bX, float bY) {
        boolean outOfBounds = aX == -1 || aY == -1 || bX == -1 || bY == -1;
        if (outOfBounds) return true;

        boolean horizontalAsymptote = (aX > getXAxisMax() && bX < getXAxisMin()) || (aX < getXAxisMin() && bX > getXAxisMax());
        boolean verticalAsymptote = (aY > getYAxisMax() && bY < getYAxisMin()) || (aY < getYAxisMin() && bY > getYAxisMax());
        return horizontalAsymptote || verticalAsymptote;
    }

    public float getXAxisMin() {
        return (mOffsetX - 1) * mZoomLevel;
    }

    public float getXAxisMax() {
        int numOfHorizontalGridLines = getWidth() / mLineMargin + 1;
        return (numOfHorizontalGridLines + mOffsetX) * mZoomLevel;
    }

    public float getYAxisMin() {
        int numOfVerticalGridLines = getHeight() / mLineMargin + 1;
        return -1 * (numOfVerticalGridLines + mOffsetY) * mZoomLevel;
    }

    public float getYAxisMax() {
        return -1 * (mOffsetY - 1) * mZoomLevel;
    }

    @Override
    public void setBackgroundColor(int color) {
        mBackgroundPaint.setColor(color);
    }

    private void setMode(MotionEvent e) {
        mPointers = e.getPointerCount();
        switch (e.getPointerCount()) {
            case 1:
                // Drag
                setMode(DRAG, e);
                break;
            case 2:
                // Zoom
                setMode(ZOOM, e);
                break;
        }
    }

    private void setMode(int mode, MotionEvent e) {
        mMode = mode;
        switch (mode) {
            case DRAG:
                mStartX = e.getX();
                mStartY = e.getY();
                mDragOffsetX = 0;
                mDragOffsetY = 0;
                mDragRemainderX = 0;
                mDragRemainderY = 0;
                break;
            case ZOOM:
                cancelZoomAnimation();
                mZoomInitDistance = getDistance(new Point(e.getX(0), e.getY(0)), new Point(e.getX(1), e.getY(1)));
                mZoomInitUnitsPerPixel = mZoomLevel / (float) mLineMargin;
                break;
        }
    }

    /** How much of the graph one grid line stands for. Always 1, 2 or 5 times a power of ten. */
    public float getZoomLevel() {
        return mZoomLevel;
    }

    public void setZoomLevel(float level) {
        cancelZoomAnimation();
        mZoomLevel = level;
        mLineMargin = mBaseLineMargin;
        invalidate();
        for (ZoomListener listener : mZoomListeners) {
            listener.zoomApplied(mZoomLevel);
        }
    }

    public void zoomIn() {
        animateZoomTo(nextNiceNumber(mZoomLevel, false));
    }

    public void zoomOut() {
        animateZoomTo(nextNiceNumber(mZoomLevel, true));
    }

    /**
     * Scales the graph so that a pixel is worth {@code unitsPerPixel}, leaving whatever is under
     * ({@code focusX}, {@code focusY}) where it is.
     *
     * <p>The number a grid line stands for is snapped to the nearest 1, 2 or 5, and the spacing
     * between lines takes up the difference. So as you pinch, the lines slide apart, and once they
     * are far enough apart the numbers step to the next size and the spacing springs back.
     */
    private void zoomTo(float unitsPerPixel, float focusX, float focusY) {
        if (unitsPerPixel <= 0 || Float.isNaN(unitsPerPixel) || Float.isInfinite(unitsPerPixel)) {
            return;
        }

        float level = (float) snapToNiceNumber(unitsPerPixel * mBaseLineMargin);
        int lineMargin = Math.max(1, Math.round(level / unitsPerPixel));
        boolean levelChanged = level != mZoomLevel;

        // Remember where the focus is pointing before the scale changes underneath it.
        float focusedX = toGraphX(focusX);
        float focusedY = toGraphY(focusY);

        mZoomLevel = level;
        mLineMargin = lineMargin;
        mGraphIsCentered = false;

        // And put it back under the finger.
        panBy(focusX - toPixelX(focusedX), focusY - toPixelY(focusedY));

        // Only tell listeners when the step changes. They redraw every curve from scratch, which is
        // far too much work to do on every frame of a pinch, and the curves already scale with us.
        if (levelChanged) {
            notifyZoomed();
        }
        invalidate();
    }

    private void notifyZoomed() {
        for (ZoomListener listener : mZoomListeners) {
            listener.zoomApplied(mZoomLevel);
        }
    }

    private void animateZoomTo(float level) {
        cancelZoomAnimation();
        if (getWidth() == 0 || getHeight() == 0) {
            setZoomLevel(level);
            return;
        }

        final float focusX = getWidth() / 2f;
        final float focusY = getHeight() / 2f;
        mZoomAnimator = ValueAnimator.ofFloat(mZoomLevel / (float) mLineMargin, level / (float) mBaseLineMargin);
        mZoomAnimator.setDuration(ZOOM_DURATION);
        mZoomAnimator.setInterpolator(new DecelerateInterpolator());
        mZoomAnimator.addUpdateListener(animation ->
                zoomTo((float) animation.getAnimatedValue(), focusX, focusY));
        mZoomAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                notifyZoomed();
            }
        });
        mZoomAnimator.start();
    }

    private void cancelZoomAnimation() {
        if (mZoomAnimator != null) {
            mZoomAnimator.cancel();
            mZoomAnimator = null;
        }
    }

    /** The nearest number of the form 1, 2 or 5 times a power of ten. */
    static double snapToNiceNumber(double value) {
        if (value <= 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return 1;
        }

        double decade = Math.pow(10, Math.floor(Math.log10(value)));
        double nearest = decade;
        double nearestRatio = Double.MAX_VALUE;
        for (double niceNumber : NICE_NUMBERS) {
            double candidate = decade * niceNumber;
            double ratio = candidate > value ? candidate / value : value / candidate;
            if (ratio < nearestRatio) {
                nearestRatio = ratio;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** The next number of the form 1, 2 or 5 times a power of ten, one step up or down from here. */
    static float nextNiceNumber(double value, boolean larger) {
        double from = snapToNiceNumber(value);
        double decade = Math.pow(10, Math.floor(Math.log10(from) + 1e-6));
        double mantissa = from / decade;
        if (larger) {
            return (float) (decade * (mantissa < 1.5 ? 2 : mantissa < 3.5 ? 5 : 10));
        }
        return (float) (decade * (mantissa > 3.5 ? 2 : mantissa > 1.5 ? 1 : 0.5));
    }

    public void addGraph(Graph graph) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new RuntimeException("addGraph called from a thread other than the ui thread");
        }

        mData.add(graph);
        postInvalidate();
    }

    public void clearGraphs() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new RuntimeException("clearGraphs called from a thread other than the ui thread");
        }

        mData.clear();
        mInspectedGraph = null;
        mInspectingArea = false;
        mAreaBoundCount = 0;
        postInvalidate();
    }

    public void setOnCenterListener(OnCenterListener l) {
        mOnCenterListener = l;
    }

    public List<Graph> getGraphs() {
        return mData;
    }

    private double getDistance(Point a, Point b) {
        return Math.sqrt(square(a.getX() - b.getX()) + square(a.getY() - b.getY()));
    }

    private double square(double val) {
        return val * val;
    }

    public void setGridColor(int color) {
        mGridPaint.setColor(color);
    }

    public void setAxisColor(int color) {
        if (mAxisPaint == null) {
            mAxisPaint = new Paint();
            mAxisPaint.setColor(Color.LTGRAY);
            mAxisPaint.setStyle(Style.STROKE);
            mAxisPaint.setStrokeWidth(mGridWidth);
        }
        mAxisPaint.setColor(color);
    }

    public void setTextColor(int color) {
        mTextPaint.setColor(color);
        invalidate();
    }

    /**
     * The colour of the readout a tap puts on the graph. Kept apart from the colour of the numbers
     * along the axes, which is usually faint enough to disappear into the grid.
     */
    public void setInspectionTextColor(int color) {
        mInspectionTextPaint.setColor(color);
        invalidate();
    }

    public void setGridSize(int px) {
        mGridWidth = px;
        mGridPaint.setStrokeWidth(mGridWidth);
    }

    public void setAxisSize(int px) {
        mAxisWidth = px;
    }

    public void setGraphSize(int px) {
        mGraphWidth = px;
        mGraphPaint.setStrokeWidth(mGraphWidth);
        mDebugPaint.setStrokeWidth(mGraphWidth);
    }

    public void setBorderSize(int px) {
        mBorderWidth = px;
    }

    public void addPanListener(PanListener l) {
        mPanListeners.add(l);
    }

    public void removePanListener(PanListener l) {
        mPanListeners.remove(l);
    }

    public void addZoomListener(ZoomListener l) {
        mZoomListeners.add(l);
    }

    public void removeZoomListener(ZoomListener l) {
        mZoomListeners.remove(l);
    }

    public boolean isGridShown() {
        return mShowGrid;
    }

    public void setShowGrid(boolean show) {
        mShowGrid = show;
    }

    public boolean isAxisShown() {
        return mShowAxis;
    }

    public void setShowAxis(boolean show) {
        mShowAxis = show;
    }

    public boolean isOutlineShown() {
        return mShowOutline;
    }

    public void setShowOutline(boolean show) {
        mShowOutline = show;
    }

    public boolean isPanEnabled() {
        return mPanEnabled;
    }

    public void setPanEnabled(boolean enabled) {
        mPanEnabled = enabled;
    }

    public boolean isZoomEnabled() {
        return mZoomEnabled;
    }

    public void setZoomEnabled(boolean enabled) {
        mZoomEnabled = enabled;
    }

    public boolean showInlineNumbers() {
        return mInlineNumbers;
    }

    public void setShowInlineNumbers(boolean show) {
        mInlineNumbers = show;
    }

    public void panBy(float x, float y) {
        mRemainderX += Math.round(x);
        mRemainderY += Math.round(y);
        normalizeOffsets();
        invalidate();
    }

    /**
     * Rolls whole grid lines out of the leftover pixels. Without this the leftovers grow without
     * bound as you pan, and stop meaning "how far past the last line we are".
     */
    private void normalizeOffsets() {
        int linesX = (int) Math.floor((double) mRemainderX / mLineMargin);
        mOffsetX -= linesX;
        mRemainderX -= linesX * mLineMargin;

        int linesY = (int) Math.floor((double) mRemainderY / mLineMargin);
        mOffsetY -= linesY;
        mRemainderY -= linesY * mLineMargin;
    }

    /**
     * Lets a tap on the graph put a readout on it: tap a curve for the value and slope at that
     * point, and drag the circle along the curve to read off other points; tap the space between a
     * curve and the x axis to shade it in and read off the area.
     *
     * <p>Off by default, because a graph small enough to be a thumbnail has no room for it.
     */
    public void setInspectionEnabled(boolean enabled) {
        mInspectionEnabled = enabled;
        if (!enabled) {
            clearInspection();
        }
    }

    public boolean isInspectionEnabled() {
        return mInspectionEnabled;
    }

    /** Takes the readout back off the graph. */
    public void clearInspection() {
        mInspectedGraph = null;
        mInspectingArea = false;
        mAreaBoundCount = 0;
        mDraggingInspection = false;
        invalidate();
    }

    /** How many ends of the shaded area have been chosen by tapping the curve, 0 to 2. */
    public int getAreaBoundCount() {
        return mAreaBoundCount;
    }

    /** Where one of those ends sits, in the graph's units. */
    public float getAreaBound(int index) {
        return mAreaBounds[index];
    }

    /** The graph the readout is on, or null when there isn't one. */
    @Nullable
    public Graph getInspectedGraph() {
        return mInspectedGraph;
    }

    /** True when the readout is showing the shaded area rather than a point on the curve. */
    public boolean isInspectingArea() {
        return mInspectedGraph != null && mInspectingArea;
    }

    /** Where along the graph the readout sits, in the graph's own units. */
    public float getInspectedX() {
        return mInspectedX;
    }

    private void inspectAt(float pixelX, float pixelY) {
        Graph onTheCurve = graphNearPoint(pixelX, pixelY);

        // With an area already shaded, a tap on its own curve cuts the area short there rather than
        // throwing it away for a point. Two taps make an area with both ends chosen.
        if (onTheCurve != null && onTheCurve == mInspectedGraph && mInspectingArea) {
            if (mAreaBoundCount == mAreaBounds.length) {
                mAreaBoundCount = 0;
            }
            mAreaBounds[mAreaBoundCount++] = toGraphX(pixelX);
            invalidate();
            return;
        }

        if (onTheCurve != null) {
            mInspectedGraph = onTheCurve;
            mInspectingArea = false;
        } else {
            mInspectedGraph = graphOverPoint(pixelX, pixelY);
            mInspectingArea = mInspectedGraph != null;
        }
        mAreaBoundCount = 0;
        mInspectedX = toGraphX(pixelX);
        invalidate();
    }

    private void dragReadoutTo(float pixelX) {
        if (mDraggingBound >= 0) {
            mAreaBounds[mDraggingBound] = toGraphX(pixelX);
        } else {
            mInspectedX = toGraphX(pixelX);
        }
        invalidate();
    }

    /**
     * True when this touch landed on something the readout lets you move: the circle on the curve,
     * or one of the lines cutting an area short. Both are a little larger to touch than to look at.
     */
    private boolean isOnTheReadout(float pixelX, float pixelY) {
        mDraggingBound = -1;
        if (mInspectedGraph == null) {
            return false;
        }

        if (mInspectingArea) {
            for (int i = 0; i < mAreaBoundCount; i++) {
                if (Math.abs(pixelX - toPixelX(mAreaBounds[i])) <= mInspectionRadius * 2) {
                    mDraggingBound = i;
                    return true;
                }
            }
            return false;
        }

        Float y = valueAt(mInspectedGraph, mInspectedX);
        if (y == null) {
            return false;
        }
        double distance = Math.hypot(pixelX - toPixelX(mInspectedX), pixelY - toPixelY(y));
        return distance <= mInspectionRadius * 3;
    }

    /** The visible curve passing nearest this point, if one passes close enough to have been meant. */
    @Nullable
    private Graph graphNearPoint(float pixelX, float pixelY) {
        Graph nearest = null;
        double nearestDistance = mTouchSlop * 2;
        for (Graph graph : mData) {
            if (!graph.isVisible()) {
                continue;
            }

            List<Point> data = graph.getData();
            for (int i = 1; i < data.size(); i++) {
                float aX = toPixelX(data.get(i - 1).getX());
                float aY = toPixelY(data.get(i - 1).getY());
                float bX = toPixelX(data.get(i).getX());
                float bY = toPixelY(data.get(i).getY());
                if (!isReal(aX) || !isReal(aY) || !isReal(bX) || !isReal(bY)) {
                    continue;
                }

                double distance = distanceToSegment(pixelX, pixelY, aX, aY, bX, bY);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = graph;
                }
            }
        }
        return nearest;
    }

    /** The visible curve this point sits underneath, in the space between the curve and the axis. */
    @Nullable
    private Graph graphOverPoint(float pixelX, float pixelY) {
        float x = toGraphX(pixelX);
        float y = toGraphY(pixelY);
        for (Graph graph : mData) {
            if (!graph.isVisible()) {
                continue;
            }

            Float curve = valueAt(graph, x);
            if (curve != null && ((y >= 0 && y <= curve) || (y <= 0 && y >= curve))) {
                return graph;
            }
        }
        return null;
    }

    /** The two points either side of this x, or null where the graph doesn't reach. */
    @Nullable
    private static Point[] segmentAt(Graph graph, float x) {
        List<Point> data = graph.getData();
        for (int i = 1; i < data.size(); i++) {
            Point a = data.get(i - 1);
            Point b = data.get(i);
            if (!isReal(a.getX()) || !isReal(b.getX())) {
                continue;
            }
            if ((x >= a.getX() && x <= b.getX()) || (x >= b.getX() && x <= a.getX())) {
                return new Point[] {a, b};
            }
        }
        return null;
    }

    @Nullable
    static Float valueAt(Graph graph, float x) {
        Point[] segment = segmentAt(graph, x);
        if (segment == null || !isReal(segment[0].getY()) || !isReal(segment[1].getY())) {
            return null;
        }

        float aX = segment[0].getX();
        float bX = segment[1].getX();
        if (aX == bX) {
            return segment[0].getY();
        }
        return segment[0].getY() + (segment[1].getY() - segment[0].getY()) * (x - aX) / (bX - aX);
    }

    /** The first sample at or after {@code x}, or -1 where the graph doesn't reach. */
    private static int indexAfter(List<Point> data, float x) {
        for (int i = 1; i < data.size(); i++) {
            if (x >= data.get(i - 1).getX() && x <= data.get(i).getX()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * How steeply the graph is climbing at this x, measured across the samples either side of the
     * nearest one. Measured forward from the point instead, the slope leans the way the curve is
     * bending: on y = x squared it reads 4.1 at x = 2, where the answer is 4.
     */
    @Nullable
    static Float slopeAt(Graph graph, float x) {
        List<Point> data = graph.getData();
        int after = indexAfter(data, x);
        if (after < 0) {
            return null;
        }

        int nearest = x - data.get(after - 1).getX() <= data.get(after).getX() - x
                ? after - 1 : after;
        Point before = data.get(Math.max(0, nearest - 1));
        Point beyond = data.get(Math.min(data.size() - 1, nearest + 1));
        if (before.getX() == beyond.getX()
                || !isReal(before.getY()) || !isReal(beyond.getY())
                || !isReal(before.getX()) || !isReal(beyond.getX())) {
            return null;
        }
        return (beyond.getY() - before.getY()) / (beyond.getX() - before.getX());
    }

    /**
     * A patch of graph caught between the curve and the x axis. It runs from one crossing of the
     * axis to the next, which is what closes it off and gives it an area at all. Where the curve
     * runs off the end of what has been drawn without crossing back, nothing closes it and the
     * area is infinite.
     */
    static class Area {
        float from;
        float to;
        float value;
        boolean closed;

        /** The area, or the infinity it runs off to, with the sign it goes in. */
        String describe(DecimalFormat format) {
            if (closed) {
                return format.format(value);
            }
            return (value < 0 ? "-" : "") + "∞";
        }
    }

    /**
     * The patch of graph between the curve and the axis that {@code x} falls in: from where the
     * curve last crossed the axis to where it crosses back, or as far as the curve has been drawn.
     */
    @Nullable
    static Area areaAround(Graph graph, float x) {
        Float here = valueAt(graph, x);
        if (here == null || here == 0) {
            return null;
        }

        List<Point> data = graph.getData();
        int after = indexAfter(data, x);
        if (after < 0) {
            return null;
        }

        boolean above = here > 0;
        Area area = new Area();
        float start = crossing(data, after - 1, -1, above);
        float end = crossing(data, after, 1, above);
        area.closed = isReal(start) && isReal(end);
        area.from = isReal(start) ? start : data.get(0).getX();
        area.to = isReal(end) ? end : data.get(data.size() - 1).getX();
        area.value = areaUnder(graph, area.from, area.to);
        return area;
    }

    /**
     * Walks the curve from {@code start} in the given direction until it crosses the axis, and
     * returns where it crossed. NaN if it never does, or if the curve breaks off first.
     */
    private static float crossing(List<Point> data, int start, int step, boolean above) {
        for (int i = start; i >= 0 && i < data.size(); i += step) {
            float y = data.get(i).getY();
            if (!isReal(y) || !isReal(data.get(i).getX())) {
                return Float.NaN;
            }
            if (above ? y < 0 : y > 0) {
                Point crossed = data.get(i);
                Point before = data.get(i - step);
                if (crossed.getY() == before.getY()) {
                    return crossed.getX();
                }
                return crossed.getX() + (before.getX() - crossed.getX())
                        * (0 - crossed.getY()) / (before.getY() - crossed.getY());
            }
        }
        return Float.NaN;
    }

    /** The area between the curve and the x axis, counting anything below the axis as negative. */
    static float areaUnder(Graph graph, float from, float to) {
        List<Point> data = graph.getData();
        float area = 0;
        for (int i = 1; i < data.size(); i++) {
            float aX = data.get(i - 1).getX();
            float aY = data.get(i - 1).getY();
            float bX = data.get(i).getX();
            float bY = data.get(i).getY();
            if (bX < aX) {
                float swap = aX; aX = bX; bX = swap;
                swap = aY; aY = bY; bY = swap;
            }
            if (bX <= from || aX >= to || aX == bX
                    || !isReal(aX) || !isReal(bX) || !isReal(aY) || !isReal(bY)) {
                continue;
            }

            // Trapezoid, with the ends trimmed to the window we're measuring.
            float left = Math.max(aX, from);
            float right = Math.min(bX, to);
            float leftY = aY + (bY - aY) * (left - aX) / (bX - aX);
            float rightY = aY + (bY - aY) * (right - aX) / (bX - aX);
            area += (leftY + rightY) / 2 * (right - left);
        }
        return area;
    }

    private void drawInspection(Canvas canvas) {
        // Turning inspection off clears the graph it was on, so there is nothing to draw either way.
        Graph graph = mInspectedGraph;
        if (graph == null || !graph.isVisible()) {
            return;
        }

        if (mInspectingArea) {
            drawAreaUnder(canvas, graph);
        } else {
            drawPointOn(canvas, graph);
        }
    }

    private void drawPointOn(Canvas canvas, Graph graph) {
        Float y = valueAt(graph, mInspectedX);
        if (y == null) {
            return;
        }

        float pixelX = toPixelX(mInspectedX);
        float pixelY = toPixelY(y);
        if (!isReal(pixelX) || !isReal(pixelY)) {
            return;
        }

        Float slope = slopeAt(graph, mInspectedX);
        if (slope != null) {
            // The slope drawn as the line it describes. A pixel is worth the same amount across as
            // it is up, so the slope carries straight over, with the sign flipped for the screen.
            mInspectionPaint.setStyle(Style.STROKE);
            mInspectionPaint.setStrokeWidth(mGraphWidth);
            mInspectionPaint.setColor(faded(graph.getColor()));
            canvas.drawLine(
                    0, pixelY + slope * pixelX,
                    getWidth(), pixelY - slope * (getWidth() - pixelX),
                    mInspectionPaint);

            // The number belongs to the line, so it is set off along it, away from the point. Two
            // readouts stacked in one box is more than the eye wants to take in at once.
            float along = pixelX > getWidth() / 2f ? -mSlopeLabelOffset : mSlopeLabelOffset;
            drawReadout(canvas, pixelX + along, pixelY - slope * along - mInspectionRadius,
                    graph.getColor(), "dy/dx = " + mReadoutFormat.format(slope));
        }

        // A ring rather than a dot, so the curve stays visible through it.
        mInspectionPaint.setStyle(Style.FILL);
        mInspectionPaint.setColor(graph.getColor());
        canvas.drawCircle(pixelX, pixelY, mInspectionRadius, mInspectionPaint);
        mInspectionPaint.setColor(mBackgroundPaint.getColor());
        canvas.drawCircle(pixelX, pixelY, mInspectionRadius - mGraphWidth, mInspectionPaint);

        drawReadout(canvas, pixelX, pixelY - mInspectionRadius * 2, graph.getColor(),
                mReadoutFormat.format(mInspectedX) + ", " + mReadoutFormat.format(y));
    }

    /** The stretch of curve the area covers: between the bounds if both are set, else its own. */
    @Nullable
    private Area currentArea(Graph graph) {
        if (mAreaBoundCount < mAreaBounds.length) {
            return areaAround(graph, mInspectedX);
        }

        Area area = new Area();
        area.from = Math.min(mAreaBounds[0], mAreaBounds[1]);
        area.to = Math.max(mAreaBounds[0], mAreaBounds[1]);
        area.value = areaUnder(graph, area.from, area.to);
        area.closed = true;
        return area;
    }

    private void drawAreaUnder(Canvas canvas, Graph graph) {
        Area area = currentArea(graph);
        if (area == null) {
            return;
        }

        float from = area.from;
        float to = area.to;
        float axis = toPixelY(0);
        if (!isReal(axis)) {
            return;
        }

        // Curves run off to infinity around an asymptote. Keep the path near the view or it costs a
        // great deal to fill and shows nothing extra.
        float ceiling = -getHeight();
        float floor = 2f * getHeight();

        mAreaPath.reset();
        boolean started = false;
        float lastX = 0;
        for (Point point : graph.getData()) {
            float x = point.getX();
            if (!isReal(x) || x < from || x > to) {
                continue;
            }

            float pixelX = toPixelX(x);
            float pixelY = toPixelY(point.getY());
            if (!isReal(pixelX) || !isReal(pixelY)) {
                continue;
            }
            pixelY = Math.max(ceiling, Math.min(floor, pixelY));

            if (!started) {
                mAreaPath.moveTo(pixelX, axis);
                started = true;
            }
            mAreaPath.lineTo(pixelX, pixelY);
            lastX = pixelX;
        }
        if (!started) {
            return;
        }

        mAreaPath.lineTo(lastX, axis);
        mAreaPath.close();

        mInspectionPaint.setStyle(Style.FILL);
        mInspectionPaint.setColor((graph.getColor() & 0x00ffffff) | 0x50000000);
        canvas.drawPath(mAreaPath, mInspectionPaint);

        drawAreaBounds(canvas, graph, axis);

        float middle = (toPixelX(Math.max(from, toGraphX(0)))
                + toPixelX(Math.min(to, toGraphX(getWidth())))) / 2;
        drawReadout(canvas, middle, axis, graph.getColor(), "∫ = " + area.describe(mReadoutFormat));
    }

    /** The edges the area has been cut to, each with a handle on the curve to drag it by. */
    private void drawAreaBounds(Canvas canvas, Graph graph, float axis) {
        for (int i = 0; i < mAreaBoundCount; i++) {
            float pixelX = toPixelX(mAreaBounds[i]);
            Float y = valueAt(graph, mAreaBounds[i]);
            if (!isReal(pixelX) || y == null) {
                continue;
            }

            float pixelY = toPixelY(y);
            mInspectionPaint.setStyle(Style.STROKE);
            mInspectionPaint.setStrokeWidth(mGraphWidth);
            mInspectionPaint.setColor(graph.getColor());
            canvas.drawLine(pixelX, pixelY, pixelX, axis, mInspectionPaint);

            mInspectionPaint.setStyle(Style.FILL);
            canvas.drawCircle(pixelX, pixelY, mInspectionRadius * 0.7f, mInspectionPaint);
        }
    }

    /**
     * A small card of one line, sitting above ({@code pixelX}, {@code pixelY}) and outlined in
     * {@code accent} so it is clear which curve it belongs to.
     */
    private void drawReadout(Canvas canvas, float pixelX, float pixelY, int accent, String text) {
        float padding = fromDp(8);
        float width = mInspectionTextPaint.measureText(text);
        float height = mInspectionTextPaint.getTextSize() * 1.25f;

        float left = pixelX - width / 2 - padding;
        float top = pixelY - height - 2 * padding;
        left = Math.min(getWidth() - width - 2 * padding - mReadoutBorderWidth,
                Math.max(mReadoutBorderWidth, left));
        top = Math.min(getHeight() - height - 2 * padding - mReadoutBorderWidth,
                Math.max(mReadoutBorderWidth, top));
        float right = left + width + 2 * padding;
        float bottom = top + height + 2 * padding;

        // Solid, so the curve and grid don't read through the numbers.
        mInspectionPaint.setStyle(Style.FILL);
        mInspectionPaint.setColor(mBackgroundPaint.getColor() | 0xff000000);
        canvas.drawRoundRect(left, top, right, bottom, padding, padding, mInspectionPaint);

        mInspectionPaint.setStyle(Style.STROKE);
        mInspectionPaint.setStrokeWidth(mReadoutBorderWidth);
        mInspectionPaint.setColor(accent);
        canvas.drawRoundRect(left, top, right, bottom, padding, padding, mInspectionPaint);
        mInspectionPaint.setStyle(Style.FILL);

        canvas.drawText(text, left + padding, top + padding + mInspectionTextPaint.getTextSize(),
                mInspectionTextPaint);
    }

    private static double distanceToSegment(float x, float y, float aX, float aY, float bX, float bY) {
        float lengthSquared = (bX - aX) * (bX - aX) + (bY - aY) * (bY - aY);
        if (lengthSquared == 0) {
            return Math.hypot(x - aX, y - aY);
        }

        float along = ((x - aX) * (bX - aX) + (y - aY) * (bY - aY)) / lengthSquared;
        along = Math.max(0, Math.min(1, along));
        return Math.hypot(x - (aX + along * (bX - aX)), y - (aY + along * (bY - aY)));
    }

    private static boolean isReal(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    /** The same colour, faint enough to read as a hint rather than as part of the graph. */
    private static int faded(int color) {
        return (color & 0x00ffffff) | 0x66000000;
    }

    public interface PanListener {
        void panApplied();
    }

    public interface ZoomListener {
        void zoomApplied(float level);
    }

    public interface OnCenterListener {
        void onCentered();
    }

    public static class Graph {
        private String formula;
        private int color;
        private List<Point> data;
        private boolean visible = true;

        public Graph(String formula, int color, List<Point> data) {
            this.formula = formula;
            this.color = color;
            this.data = data;
        }

        public String getFormula() {
            return formula;
        }

        public void setFormula(String formula) {
            this.formula = formula;
        }

        public int getColor() {
            return color;
        }

        public void setColor(int color) {
            this.color = color;
        }

        public List<Point> getData() {
            return data;
        }

        public void setData(List<Point> data) {
            this.data = data;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        @Override
        public String toString() {
            return String.format("Graph{formula=%s}", formula);
        }
    }
}
