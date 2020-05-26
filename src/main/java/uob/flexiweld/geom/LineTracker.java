package uob.flexiweld.geom;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import uob.flexiweld.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@code LineTracker} object allows lines in a video stream to be tracked using a 'fuzzy moving average' method. The
 * primary method, {@link LineTracker#processNextFrame(Mat)}, processes a single frame of the video to extract the
 * lines. Each {@code LineTracker} maintains a record of the raw lines detected for a given number of previous frames,
 * as specified in the constructor. This data is used to perform the aforementioned fuzzy moving average.
 * <p></p>
 * A variety of parameters may also be set to change the behaviour of the line detector and the averaging process. These
 * default to generally-applicable values if not set.
 * @see Line
 * @author Finin Quincey
 */
public class LineTracker {

	/** The maximum number of lines that can be displayed at once (does not affect processing, only display) */
	private static final int MAX_DISPLAYED_LINES = 50;

	/** Number of frames to average over when performing fuzzy average of lines */
	public final int interpFrames;

	// These values may be changed between frames
	/** Lines within this angle of each other are considered parallel */
	private double angleThreshold = Math.toRadians(10);
	/** Parallel lines within this distance of each other are considered coincident */
	private double proximityThreshold = 10;
	/** Lines whose endpoints both lie within this distance of the edge of the frame will be discarded */
	private double border = 20;
	/** Whether the raw detected lines will be drawn onto the frame when {@link LineTracker#processNextFrame(Mat)} is called */
	private boolean annotations = false;
	/** Whether {@link LineTracker#processNextFrame(Mat)} returns the result of the edge detector instead of the original frame. */
	private boolean showEdges = false;

	/** Stores the lines from the last n frames for averaging. Each sub-list is one frame, ordered oldest to newest. */
	private final List<List<Line>> prevLines = new ArrayList<>();

	/**
	 * Constructs a new {@code LineTracker} which averages over the given number of frames.
	 * @param interpFrames The number of frames to perform the fuzzy moving average over. Higher numbers result in
	 *                     more stable lines, but react more slowly to movement and take longer to process.
	 */
	public LineTracker(int interpFrames){
		this.interpFrames = interpFrames;
	}

	/**
	 * Sets the angle threshold for this line tracker. Lines with less than this angle between them will be considered
	 * parallel. This value is 10 degrees by default.
	 * @param degrees The angle threshold, in degrees
	 * @return The {@code LineTracker} object, allowing this method to be chained onto the constructor.
	 */
	public LineTracker angleThreshold(double degrees){
		this.angleThreshold = Math.toRadians(degrees);
		return this;
	}

	/**
	 * Sets the proximity threshold for this line tracker. Parallel lines less than this many pixels apart on average
	 * will be considered coincident. This value is
	 * @param pixels The proximity threshold, in pixels
	 * @return The {@code LineTracker} object, allowing this method to be chained onto the constructor.
	 */
	public LineTracker proximityThreshold(double pixels){
		this.proximityThreshold = pixels;
		return this;
	}

	/**
	 * Sets the border size for this line tracker. Lines whose endpoints both lie within this distance of any edge of
	 * the frame will be discarded.
	 * @param pixels The border size, in pixels
	 * @return The {@code LineTracker} object, allowing this method to be chained onto the constructor.
	 */
	public LineTracker border(double pixels){
		this.border = pixels;
		return this;
	}

	/**
	 * Sets whether the original frame or the result of the edge detector will be returned by
	 * {@link LineTracker#processNextFrame(Mat)}.
	 * @param edges True to return the result of the edge detector, false to return the original frame.
	 * @return The {@code LineTracker} object, allowing this method to be chained onto the constructor.
	 */
	public LineTracker edges(boolean edges){
		this.showEdges = edges;
		return this;
	}

	/**
	 * Sets whether the raw detected lines will be drawn onto the frame when {@link LineTracker#processNextFrame(Mat)}
	 * is called.
	 * @param annotations True to enable annotations, false to disable them.
	 * @return The {@code LineTracker} object, allowing this method to be chained onto the constructor.
	 */
	public LineTracker annotations(boolean annotations){
		this.annotations = annotations;
		return this;
	}

	/**
	 * Processes the given frame to extract lines, performs a fuzzy moving average with the previous n frames (where n
	 * is the number specified on creation), discards the oldest frame and stores the new one.
	 * @param frame The new frame to be processed (this frame will only be modified if annotations are enabled).
	 * @return The set of lines resulting from the fuzzy average of the lines in the given frame and the previous frames.
	 */
	public List<Line> processNextFrame(Mat frame){

		// Canny edge detector
		Mat edges = Utils.process(frame, (s, d) -> Imgproc.Canny(s, d, 50, 200, 3, false));

		// Hough line transform
		List<Line> lines = extractLines(edges, border);

		// Display
		if(showEdges) frame = Utils.process(edges, (s, d) -> Imgproc.cvtColor(s, d, Imgproc.COLOR_GRAY2BGR));

		if(annotations){
			for(int i = 0; i < Math.min(lines.size()-1, MAX_DISPLAYED_LINES); i++){
				Line line = lines.get(i);
				Imgproc.line(frame, line.getStart(), line.getEnd(), Utils.GREEN, 2, Imgproc.LINE_AA, 0);
			}
		}

		// TODO: Why is this here? It definitely needs to be, but there should be a comment explaining why!
		lines.sort(Comparator.comparing(Line::angle));

		prevLines.add(lines);

		if(prevLines.size() > interpFrames) prevLines.remove(0);

		List<Line> allPrevLines = new ArrayList<>(Utils.flatten(prevLines));
		Collections.reverse(allPrevLines); // Do the more recent lines first

		return fuzzyAverageLines(allPrevLines, proximityThreshold, angleThreshold);

	}

	/**
	 * Performs a probabilistic Hough line transform on the given image source and converts the resulting matrix to a
	 * list of {@link Line} objects.
	 * @param source The image from which lines are to be extracted, usually the output of an edge detection filter
	 * @param border Detected lines whose ends both lie within this distance of the edge of the image will be ignored
	 * @return The resulting list of {@code Line} objects (in the order returned by the Hough line transform)
	 */
	private static List<Line> extractLines(Mat source, double border){

		// Probabilistic Hough Line Transform
		Mat lineMatrix = new Mat(); // will hold the results of the detection
		Imgproc.HoughLinesP(source, lineMatrix, 1, Math.PI / 180, 40, 20, 20); // runs the actual detection

		List<Line> lines = new ArrayList<>();

		// Convert result matrix to a list of lines
		for(int i = 0; i < lineMatrix.rows(); i++){

			double[] l = lineMatrix.get(i, 0);
			// Discard lines that are on the edge of the frame, we don't want to detect the edge
			if(l[0] < border && l[2] < border || l[0] > source.width() - border && l[2] > source.width() - border
			|| l[1] < border && l[3] < border || l[1] > source.height() - border && l[3] > source.height() - border)
				continue;
			// Create the line, add to the list and draw it
			Line line = new Line(l[0], l[1], l[2], l[3]);
			lines.add(line);
		}

		return lines;
	}

	/**
	 * Performs a 'fuzzy average' of the given list of lines. Lines within the given distance and angle thresholds of
	 * each other are considered coincident and are merged by averaging their start and end points.
	 * @param lines A list of {@link Line} objects to be averaged (will not be modified by this method)
	 * @param distThreshold The maximum distance between two lines for them to be considered coincident (the distance
	 *                      between two lines is taken as the smaller of the perpendicular distances from one line to
	 *                      the midpoint of the other)
	 * @param angleThreshold The maximum (acute) angle between two lines for them to be considered coincident
	 * @return The resulting list of averaged lines
	 */
	private static List<Line> fuzzyAverageLines(List<Line> lines, double distThreshold, double angleThreshold){

		List<Line> averagedLines = new ArrayList<>();
		lines = new ArrayList<>(lines); // Make a copy so we don't modify the input list

		while(!lines.isEmpty()){

			Line ref = lines.remove(0);
			// Choose which axis to rectify/compare in based on the gradient of the reference line
			// Avoids issues with vertical lines
			final boolean compareYs = Math.abs(ref.gradient()) > 1;
			lines = lines.stream().map(compareYs ? Line::yRectified : Line::xRectified).collect(Collectors.toList());

			List<Line> coincident = new ArrayList<>();

			for(Line line : lines){
				// Determine if each line should be considered coincident with ref, and if so add to the list
				if(ref.distanceTo(line.midpoint()) < distThreshold && Line.acuteAngleBetween(ref, line) < angleThreshold){
					coincident.add(line);
				}
			}

			coincident.add(ref);

			lines.removeAll(coincident);

			// Could probably replace all of this with regression or something similar

			List<Point> starts = coincident.stream().map(Line::getStart).collect(Collectors.toList());
			List<Point> ends = coincident.stream().map(Line::getEnd).collect(Collectors.toList());

			Line averageLine = new Line(starts.stream().mapToDouble(p -> p.x).average().orElse(0),
					starts.stream().mapToDouble(p -> p.y).average().orElse(0),
					ends.stream().mapToDouble(p -> p.x).average().orElse(0),
					ends.stream().mapToDouble(p -> p.y).average().orElse(0));

			// Most extreme ends of lines - choose axis to compare by to avoid issues with vertical lines
			Point start = starts.stream().min(Comparator.comparingDouble(p -> compareYs ? p.y : p.x)).orElse(null);
			Point end = ends.stream().max(Comparator.comparingDouble(p -> compareYs ? p.y : p.x)).orElse(null);

			if(start != null && end != null){
				averageLine = new Line(averageLine.nearestPointTo(start), averageLine.nearestPointTo(end));
				averagedLines.add(averageLine);
			}
		}

		return averagedLines;
	}

}
