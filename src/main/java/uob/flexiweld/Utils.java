package uob.flexiweld;

import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiConsumer;

/**
 * Static utility class for various helper methods.
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

		return new Line[]{
				new Line(0, size.height * 0.1, size.width, size.height * 0.1),
				new Line(0, size.height * 0.2, size.width, size.height * 0.2),
				new Line(0, size.height * 0.3, size.width, size.height * 0.3),
				new Line(0, size.height * 0.4, size.width, size.height * 0.4),
				new Line(0, size.height * 0.5, size.width, size.height * 0.5),
				new Line(0, size.height * 0.6, size.width, size.height * 0.6),
				new Line(0, size.height * 0.7, size.width, size.height * 0.7),
				new Line(0, size.height * 0.8, size.width, size.height * 0.8),
				new Line(0, size.height * 0.9, size.width, size.height * 0.9),
				new Line(size.width * 0.1, 0, size.width * 0.1, size.height),
				new Line(size.width * 0.2, 0, size.width * 0.2, size.height),
				new Line(size.width * 0.3, 0, size.width * 0.3, size.height),
				new Line(size.width * 0.4, 0, size.width * 0.4, size.height),
				new Line(size.width * 0.5, 0, size.width * 0.5, size.height),
				new Line(size.width * 0.6, 0, size.width * 0.6, size.height),
				new Line(size.width * 0.7, 0, size.width * 0.7, size.height),
				new Line(size.width * 0.8, 0, size.width * 0.8, size.height),
				new Line(size.width * 0.9, 0, size.width * 0.9, size.height),
		};
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

}
