package uob.flexiweld.app;

import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;
import uob.flexiweld.Utils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Container object for a camera matrix and distortion coefficient matrix. Also handles file saving and loading.
 * {@code CalibrationSettings} objects are immutable.
 * @author Finin Quincey
 */
public class CalibrationSettings {

	private final Mat cameraMatrix;
	private final MatOfDouble distCoeffs;

	/**
	 * Creates a new {@code CalibrationSettings} object from the given calibration output matrices. The input matrices
	 * will be copied to ensure they cannot change, and may therefore be safely modified afterwards.
	 */
	public CalibrationSettings(Mat cameraMatrix, MatOfDouble distCoeffs){
		this.cameraMatrix = cameraMatrix.clone(); // Copy so it can't change
		this.distCoeffs = new MatOfDouble(distCoeffs);
	}

	/**
	 * Undistorts the given image using this {@code CalibrationSettings} object's parameters and returns the result.
	 * @param source The image to undistort
	 * @return The resulting undistorted image.
	 */
	// We don't really want to keep cloning the matrices each frame, but since we're only going to use it for
	// undistortion we can keep it immutable by simply routing that through here
	public Mat undistort(Mat source){
		return Utils.process(source, (s, d) -> Imgproc.undistort(s, d, cameraMatrix, distCoeffs));
	}

	/**
	 * Attempts to save this calibration settings object to the given file.
	 * @param path The path of the file to save to
	 * @return True if the file was saved successfully, false otherwise.
	 */
	public boolean save(String path){
		// Try-with-resources block automatically closes the file when done or if an exception is thrown
		try(RandomAccessFile file = new RandomAccessFile(path, "rw")){

			List<Double> doubles = new ArrayList<>();
			Converters.Mat_to_vector_double(cameraMatrix.reshape(0, 9), doubles);
			for(double d : doubles) file.writeDouble(d);

			doubles.clear();
			Converters.Mat_to_vector_double(distCoeffs.t(), doubles); // Transpose because Converters requires 1 column
			file.writeInt(doubles.size()); // distCoeffs may be various lengths
			for(double d : doubles) file.writeDouble(d);

			return true;

		}catch(CvException | IOException e){
			System.err.println(String.format("Unable to save calibration file %s", path));
		}

		return false;
	}

	/**
	 * Attempts to read a calibration settings object from the given file.
	 * @param path The path of the file to read
	 * @return The resulting {@code CalibrationSettings} object, or null if the file could not be read.
	 */
	public static CalibrationSettings load(String path){
		// Try-with-resources block automatically closes the file when done or if an exception is thrown
		try(RandomAccessFile file = new RandomAccessFile(path, "rw")){

			List<Double> doubles = new ArrayList<>();
			for(int i=0; i<9; i++) doubles.add(file.readDouble());
			Mat cameraMatrix = Converters.vector_double_to_Mat(doubles).reshape(0, 3); // It's inefficient, but it's easy!

			doubles.clear();
			int length = file.readInt();
			for(int i=0; i<length; i++) doubles.add(file.readDouble());
			MatOfDouble distCoeffs = new MatOfDouble(Converters.vector_double_to_Mat(doubles).t());

			return new CalibrationSettings(cameraMatrix, distCoeffs);

		}catch(CvException | IOException e){
			System.err.println(String.format("Unable to read calibration file %s", path));
		}

		return null;
	}

}
