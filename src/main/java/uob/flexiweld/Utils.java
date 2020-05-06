package uob.flexiweld;

import org.opencv.core.*;
import uob.flexiweld.geom.Line;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Static utility class for various helper methods and constants.
 * @author Finin Quincey
 */
public final class Utils {

	// Remember these are blue-green-red (no idea why everything is BGR, seems a bit backwards to me)
	public static final Scalar WHITE = new Scalar(255, 255, 255);
	public static final Scalar BLACK = new Scalar(0, 0, 0);
	public static final Scalar RED = new Scalar(0, 0, 255);
	public static final Scalar GREEN = new Scalar(0, 255, 0);
	public static final Scalar BLUE = new Scalar(255, 0, 0);
	public static final Scalar CYAN = new Scalar(255, 255, 0);
	public static final Scalar MAGENTA = new Scalar(255, 0, 255);
	public static final Scalar YELLOW = new Scalar(0, 255, 255);

	private Utils(){} // No instances!

	/**
	 * Processes the given source image using the given operation, and returns the resulting destination image. This
	 * method eliminates the need for explicit creation of a new destination image variable each time, allowing each
	 * operation to be condensed to a one-liner, much like {@code String} manipulation. For example, a typical call
	 * might look like this:
	 * <p></p>
	 * <center>{@code frame = process(frame, (s, d) -> Imgproc.resize(s, d, new Size(width, height)));}</center>
	 * @param src The source image to be processed
	 * @param operation A {@link BiConsumer} representing the operation to be performed, usually expressed as a lambda
	 *                  expression.
	 * @return The resulting destination image. This may be assigned to the source image variable to overwrite it.
	 */
	public static Mat process(Mat src, BiConsumer<Mat, Mat> operation){
		Mat dest = Mat.zeros(src.rows(), src.cols(), src.type());
		operation.accept(src, dest);
		return dest;
	}

	/**
	 * Generates an array of {@link Line} objects which form a grid with the given dimensions that covers the area
	 * specified by the given {@link Size} object.
	 * @param rows The number of rows in the grid (the number of horizontal lines will be 1 less than this number)
	 * @param columns The number of columns in the grid (the number of vertical lines will be 1 less than this number)
	 * @param size The overall dimensions of the grid
	 * @return The resulting array of line objects
	 */
	public static Line[] generateGrid(int rows, int columns, Size size){
		return generateGrid(rows, columns, size, new Size(0, 0));
	}

	/**
	 * Generates an array of {@link Line} objects which form a grid with the given dimensions that covers the area
	 * specified by the given {@link Size} object.
	 * @param rows The number of rows in the grid (the number of horizontal lines will be 1 less than this number)
	 * @param columns The number of columns in the grid (the number of vertical lines will be 1 less than this number)
	 * @param size The overall dimensions of the grid
	 * @param offset The position of the top-left corner of the grid
	 * @return The resulting array of line objects
	 */
	public static Line[] generateGrid(int rows, int columns, Size size, Size offset){

		Line[] lines = new Line[(rows-1) + (columns-1)];

		int i = 0;

		while(i < columns-1){
			lines[i] = new Line(0 + offset.width, size.height * (i+1)/(columns) + offset.height,
					size.width + offset.width, size.height * (i+1)/(columns) + offset.height);
			i++;
		}

		while(i < lines.length){
			lines[i] = new Line(size.width * (i-columns+2)/(rows) + offset.width, 0 + offset.height,
					size.width * (i-columns+2)/(rows) + offset.width, size.height + offset.height);
			i++;
		}

		return lines;

//		return new Line[]{
//				new Line(0 + offset.width, size.height * 0.1 + offset.height, size.width + offset.width, size.height * 0.1),
//				new Line(0 + offset.width, size.height * 0.2 + offset.height, size.width + offset.width, size.height * 0.2),
//				new Line(0 + offset.width, size.height * 0.3 + offset.height, size.width + offset.width, size.height * 0.3),
//				new Line(0 + offset.width, size.height * 0.4 + offset.height, size.width + offset.width, size.height * 0.4),
//				new Line(0 + offset.width, size.height * 0.5 + offset.height, size.width + offset.width, size.height * 0.5),
//				new Line(0 + offset.width, size.height * 0.6 + offset.height, size.width + offset.width, size.height * 0.6),
//				new Line(0 + offset.width, size.height * 0.7 + offset.height, size.width + offset.width, size.height * 0.7),
//				new Line(0 + offset.width, size.height * 0.8 + offset.height, size.width + offset.width, size.height * 0.8),
//				new Line(0 + offset.width, size.height * 0.9 + offset.height, size.width + offset.width, size.height * 0.9),
//				new Line(size.width * 0.1, 0, size.width * 0.1, size.height),
//				new Line(size.width * 0.2, 0, size.width * 0.2, size.height),
//				new Line(size.width * 0.3, 0, size.width * 0.3, size.height),
//				new Line(size.width * 0.4, 0, size.width * 0.4, size.height),
//				new Line(size.width * 0.5, 0, size.width * 0.5, size.height),
//				new Line(size.width * 0.6, 0, size.width * 0.6, size.height),
//				new Line(size.width * 0.7, 0, size.width * 0.7, size.height),
//				new Line(size.width * 0.8, 0, size.width * 0.8, size.height),
//				new Line(size.width * 0.9, 0, size.width * 0.9, size.height),
//		};
	}

	public static void setupFlipMatrices(Mat mapX, Mat mapY){
		// TODO: Figure out what this is actually doing!
		// (I know what it's doing in theory, but the actual matrix manipulation bit is kinda opaque)
		float[] buffX = new float[(int)(mapX.total() * mapX.channels())];
		mapX.get(0, 0, buffX);
		float[] buffY = new float[(int)(mapY.total() * mapY.channels())];
		mapY.get(0, 0, buffY);

		for(int i = 0; i < mapX.rows(); i++){
			for(int j = 0; j < mapX.cols(); j++){
				buffX[i * mapX.cols() + j] = mapY.cols() - j;
				buffY[i * mapY.cols() + j] = i;
			}
		}

		mapX.put(0, 0, buffX);
		mapY.put(0, 0, buffY);
	}

	/**
	 * Flattens the given nested collection. The returned collection is an unmodifiable collection of all the elements
	 * contained within all of the sub-collections of the given nested collection.
	 * @param collection A nested collection to flatten
	 * @param <E> The type of elements in the given nested collection
	 * @return The resulting flattened collection.
	 */
	public static <E> Collection<E> flatten(Collection<? extends Collection<E>> collection){
		Collection<E> result = new ArrayList<>();
		collection.forEach(result::addAll);
		return Collections.unmodifiableCollection(result);
	}

	/**
	 * Detects pairs of (approximately) parallel lines that are less than the specified width apart and returns a list
	 * of lines equidistant from both lines of each pair.
	 * @param lines A list of {@link Line} objects to find centrelines for (will not be modified by this method)
	 * @param widthThreshold The maximum distance between pairs of lines for a centreline to be detected
	 * @param angleThreshold The maximum angle between pairs of lines for a centreline to be detected
	 * @return The resulting list of centrelines
	 */
	public static List<Line> findCentrelines(List<Line> lines, double widthThreshold, double angleThreshold){

		List<Line> centrelines = new ArrayList<>();

		for(int i=0; i<lines.size(); i++){

			Line lineA = lines.get(i);
			Line lineB = lines.get((i+1) % lines.size());

			if(lineA.distanceTo(lineB.midpoint()) < widthThreshold && Line.acuteAngleBetween(lineA, lineB) < angleThreshold){
				Line centreline = Line.equidistant(lineA, lineB).extended(0.25f);
				centrelines.add(centreline);
			}
		}

		return centrelines;
	}

	public static MatOfPoint3f generateChessboardPoints(Size size, double squareSize){

		MatOfPoint3f points = new MatOfPoint3f();

		for(int y = 0; y < size.height; ++y){
			for(int x = 0; x < size.width; ++x){
				// A bit unintuitive but hey, we got there in the end!
				points.push_back(new MatOfPoint3f(new Point3(y * squareSize, x * squareSize, 0)));
			}
		}

		return points;
	}

	/**
	 * Ellipsises the given string if it is longer than the given max length.
	 * @param s The string to ellipsise
	 * @param maxLength The maximum length of the string
	 * @param centre True to remove characters from the centre, false to remove them from the end
	 * @return The given string, with characters removed and replaced with ... if necessary such that it is no longer
	 * than the specified max length.
	 */
	public static String ellipsise(String s, int maxLength, boolean centre){

		if(s.length() <= maxLength) return s;

		int charsToKeep = maxLength - 3;

		if(centre){
			return s.substring(0, charsToKeep/2) + "..." + s.substring(s.length() - charsToKeep + charsToKeep/2);
		}else{
			return s.substring(0, charsToKeep) + "...";
		}
	}

}
