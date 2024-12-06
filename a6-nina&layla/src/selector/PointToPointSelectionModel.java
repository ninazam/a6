package selector;

import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Models a selection tool that connects each added point with a straight line.
 */
public class PointToPointSelectionModel extends SelectionModel {

    enum PointToPointState implements SelectionState {
        /**
         * No selection is currently in progress (no starting point has been selected).
         */
        NO_SELECTION,

        /**
         * Currently assembling a selection.  A starting point has been selected, and the selection
         * path may contain a sequence of segments, which can be appended to by adding points.
         */
        SELECTING,

        /**
         * The selection path represents a closed selection that start and ends at the same point.
         * Points may be moved, but no additional points may be added.  The selected region of the
         * image may be extracted and saved from this state.
         */
        SELECTED;

        @Override
        public boolean isEmpty() {
            return this == NO_SELECTION;
        }

        @Override
        public boolean isFinished() {
            return this == SELECTED;
        }

        @Override
        public boolean canUndo() {
            return this == SELECTED || this == SELECTING;
        }

        @Override
        public boolean canAddPoint() {
            return this == NO_SELECTION || this == SELECTING;
        }

        @Override
        public boolean canFinish() {
            return this == SELECTING;
        }

        @Override
        public boolean canEdit() {
            return this == SELECTED;
        }

        @Override
        public boolean isProcessing() {
            return false;
        }
    }

    /**
     * The current state of this selection model.
     */
    private PointToPointState state;

    /**
     * Create a model instance with no selection and no image.  If `notifyOnEdt` is true, property
     * change listeners will be notified on Swing's Event Dispatch thread, regardless of which
     * thread the event was fired from.
     */
    public PointToPointSelectionModel(boolean notifyOnEdt) {
        super(notifyOnEdt);
        state = PointToPointState.NO_SELECTION;
    }

    /**
     * Create a model instance with the same image and event notification policy as `copy`, and
     * attempt to preserve `copy`'s selection if it can be represented without violating the
     * invariants of this class.
     */
    public PointToPointSelectionModel(SelectionModel copy) {
        super(copy);
        if (copy instanceof PointToPointSelectionModel) {
            state = ((PointToPointSelectionModel) copy).state;
        } else {
            if (copy.state().isEmpty()) {
                assert segments.isEmpty() && controlPoints.isEmpty();
                state = PointToPointState.NO_SELECTION;
            } else if (!copy.state().isFinished() && controlPoints.size() == segments.size() + 1) {
                // Assumes segments start and end at control points
                state = PointToPointState.SELECTING;
            } else if (copy.state().isFinished() && controlPoints.size() == segments.size()) {
                // Assumes segments start and end at control points
                state = PointToPointState.SELECTED;
            } else {
                reset();
            }
        }
    }

    @Override
    public SelectionState state() {
        return state;
    }

    /**
     * Change our selection state to `newState` (internal operation).  This should only be used to
     * perform valid state transitions.  Notifies listeners that the "state" property has changed.
     */
    private void setState(PointToPointState newState) {
        PointToPointState oldState = state;
        state = newState;
        propSupport.firePropertyChange("state", oldState, newState);
    }

    /**
     * Return a straight line segment from our last point to `p`.
     */
    @Override
    public PolyLine liveWire(Point p) {
        // TODO 2B: Implement this method as specified by constructing and returning a new PolyLine
        //  representing the desired line segment.
        //  Test immediately with `testLiveWireStarting()`, and think about how the test might
        //  change for selections with at least one segment (see task 2D).
        return new PolyLine(lastPoint(), p);

    }

    /**
     * Add `p` as the next control point of our selection, extending our selection with a straight
     * line segment from the end of the current selection path to `p`.
     */
    @Override
    protected void appendToSelection(Point p) {
        // TODO 2C: Create a line segment from the end of the previous segment (or from the starting
        //  point if this is only the 2nd point) to the current point `p`, then append that segment
        //  to the current selection path and add a copy of `p` as the next control point.
        //  Test immediately with `testAppend()` and `testFinishSelection()`.
        segments.add(new PolyLine(lastPoint(), p));
        controlPoints.add(new Point(p));
    }

    /**
     * Move the control point with index `index` to `newPos`.  The segment that previously
     * terminated at the point should be replaced with a straight line connecting the previous point
     * to `newPos`, and the segment that previously started from the point should be replaced with a
     * straight line connecting `newPos` to the next point (where "next" and "previous" wrap around
     * as necessary). Notify listeners that the "selection" property has changed.
     */
    @Override
    public void movePoint(int index, Point newPos) {
        // Confirm that we have a closed selection and that `index` is valid
        if (!state().canEdit()) {
            throw new IllegalStateException("May not move point in state " + state());
        }
        if (index < 0 || index >= controlPoints.size()) {
            throw new IllegalArgumentException("Invalid point index " + index);
        }

        // TODO 4B: Complete the implementation of this method as specified.  Remember that `Point`
        //  is a mutable class, so you will want to _copy_ client-provided Points rather than
        //  aliasing them if you need to store them.  Finally, notify listeners that the "selection"
        //  property has changed.  Test immediately with `testMovePointMiddle()`, and add additional
        //  tests per the corresponding task in the test suite (strongly consider writing the tests
        //  first).

        LinkedList<Point> newControlPoints = new LinkedList<>(controlPoints);

        newControlPoints.set(index, newPos);

        LinkedList<PolyLine> newSegments = new LinkedList<>();
        for (int i = 0; i < newControlPoints.size(); i++) {
            Point startPoint = newControlPoints.get(i);
            Point endPoint = newControlPoints.get((i + 1) % newControlPoints.size());
            newSegments.add(new PolyLine(startPoint, endPoint));
        }

        controlPoints = newControlPoints;
        segments = newSegments;

        propSupport.firePropertyChange("selection", null, selection());
    }

    public void finishSelection() {
        if (!state.canFinish()) {
            throw new IllegalStateException("Cannot finish a selection that is already finished");
        }
        if (segments.isEmpty()) {
            reset();
        } else {
            addPoint(controlPoints.getFirst());
            // Don't double-add the starting point
            controlPoints.removeLast();
            setState(PointToPointState.SELECTED);
        }
    }

    @Override
    public void reset() {
        super.reset();
        setState(PointToPointState.NO_SELECTION);
    }

    @Override
    protected void startSelection(Point start) {
        super.startSelection(start);
        setState(PointToPointState.SELECTING);
    }

    @Override
    protected void undoPoint() {
        if (segments.isEmpty()) {
            // Reset to remove the starting point
            reset();
        } else {
            // TODO 2E: Remove the last segment of the current selection path and its associated
            //  control point (if necessary).  If the selection was finished, transition to the
            //  SELECTING state using `setState()` (and think carefully about control points).
            //  Notify listeners that the "selection" property has changed (old value should be
            //  null, while new value should be provided by the `selection()` observer to minimize
            //  rep exposure).  Test immediately with `testUndoSelected()`, and add additional tests
            //  per the corresponding task in the test suite (consider writing the tests first).
            if (!state.isFinished()) {
                controlPoints.removeLast();
                segments.removeLast();
                propSupport.firePropertyChange("selection", null, selection());
            } else {
                setState(PointToPointState.SELECTING);
                segments.removeLast();
                propSupport.firePropertyChange("selection", null, selection());
            }
        }
    }
}
