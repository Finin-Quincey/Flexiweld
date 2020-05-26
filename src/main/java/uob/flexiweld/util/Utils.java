package uob.flexiweld.util;

import org.opencv.core.*;
import uob.flexiweld.geom.Line;

import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Static utility class for various helper methods and constants.
 * @author Finin Quincey
 */
public final class Utils {

	// Remember these are blue-green-red (apparently this convention is from a very old version of OpenCV but it stuck)
	public static final Scalar WHITE = new Scalar(255, 255, 255);
	public static final Scalar BLACK = new Scalar(0, 0, 0);
	public static final Scalar RED = new Scalar(0, 0, 255);
	public static final Scalar GREEN = new Scalar(0, 255, 0);
	public static final Scalar BLUE = new Scalar(255, 0, 0);
	public static final Scalar CYAN = new Scalar(255, 255, 0);
	public static final Scalar MAGENTA = new Scalar(255, 0, 255);
	public static final Scalar YELLOW = new Scalar(0, 255, 255);

	private Utils(){} // No instances!

	// ================================================================================================================
	// Generators
	// ================================================================================================================

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

	/**
	 * Generates a matrix of points representing the corners of a checkerboard with the given parameters.
	 * @param size The number of internal corners in the checkerboard in each direction
	 * @param squareSize The width of each square in the checkerboard, in real-world units (usually millimetres)
	 * @return The resulting matrix of points
	 */
	public static MatOfPoint3f generateCheckerboardPoints(Size size, double squareSize){

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
	 * Sets up matrices for use with {@link org.opencv.imgproc.Imgproc#remap(Mat, Mat, Mat, Mat, int)}.
	 * I have since realised it's easier to use {@link Core#flip(Mat, Mat, int)}.
	 */
	public static void setupFlipMatrices(Mat mapX, Mat mapY){

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

	// ================================================================================================================
	// Processing
	// ================================================================================================================

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
	 * Detects pairs of (approximately) parallel lines that are less than the specified width apart and returns a list
	 * of lines equidistant from both lines of each pair.
	 * @param lines A list of {@link Line} objects to find centrelines for (will not be modified by this method)
	 * @param widthThreshold The maximum distance between pairs of lines for a centreline to be detected
	 * @param angleThreshold The maximum angle between pairs of lines for a centreline to be detected
	 * @return The resulting list of centrelines
	 */
	public static List<Line> findCentrelines(List<Line> lines, double widthThreshold, double angleThreshold){

		List<Line> centrelines = new ArrayList<>();

		List<Line> linesModifiable = new ArrayList<>(lines);

		while(!linesModifiable.isEmpty()){

			Line ref = linesModifiable.get(0);

			lines.stream().filter(l -> l != ref && Line.acuteAngleBetween(ref, l) < angleThreshold)
					.min(Comparator.comparingDouble(l -> ref.distanceTo(l.midpoint())))
					.ifPresent(l -> {
						if(ref.distanceTo(l.midpoint()) < widthThreshold){
							Line centreline = Line.equidistant(ref, l).extended(0.2f);
							centrelines.add(centreline);
							linesModifiable.remove(l);
						}
					});

			linesModifiable.remove(ref);
		}

		return centrelines;
	}

	// ================================================================================================================
	// Miscellaneous
	// ================================================================================================================

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

	/**
	 * Creates a new {@link FileNameExtensionFilter} with the given description and extensions, with the extensions
	 * listed at the end of the description as per the convention used in e.g. Microsoft office.
	 * @param description A description of the file type
	 * @param extensions The extensions this filter should accept
	 * @return The resulting {@code FileNameExtensionFilter}.
	 */
	public static FileNameExtensionFilter createExtensionFilter(String description, String... extensions){
		description = description + " (*." + String.join(", *.", extensions) + ")";
		return new FileNameExtensionFilter(description, extensions);
	}

	/**
	 * Converts the given {@link Color} to a {@link Scalar} for use with OpenCV methods.
	 * @param color The colour to convert
	 * @return The resulting scalar, in BGR format
	 */
	public static Scalar toScalar(Color color){
		return new Scalar(color.getBlue(), color.getGreen(), color.getRed());
	}

	/**
	 * Converts the given {@link Scalar} to a {@link Color} for use with AWT/Swing methods.
	 * @param scalar The scalar to convert, in BGR format
	 * @return The resulting colour
	 */
	public static Color toColor(Scalar scalar){
		return new Color((int)scalar.val[2], (int)scalar.val[1], (int)scalar.val[0]);
	}

}
