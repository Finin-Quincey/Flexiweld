package uob.flexiweld.app;

import org.opencv.core.Core;
import uob.flexiweld.app.mode.CaptureMode;
import uob.flexiweld.app.mode.MeasurementMode;
import uob.flexiweld.app.mode.StandbyMode;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implements a simple user interface to control the prototype tube detection software, for demonstration and testing
 * purposes. This is the main app class that sets up the {@code JFrame}, defines the UI layout, handles updating, etc.
 * @author Finin Quincey
 */
public class FlexiweldApp {

	// Constants

	/** The target framerate (in frames per second) for the display to update at. Note that this may be greater than
	 * the maximum framerate of the camera. */
	public static final int FRAMERATE = 50;

	public static final Font BUTTON_FONT = 				new Font("Segoe UI Symbol", Font.PLAIN, 24);
	public static final Font STATUS_BAR_FONT = 			new Font("Segoe UI Symbol", Font.PLAIN, 16);

	public static final Color CONTROLS_BG_COLOUR = 		new Color(0x646464);
	public static final Color BUTTON_BG_COLOUR = 		new Color(0x404040);
	public static final Color BUTTON_FG_COLOUR = 		new Color(0xcaa4ff);
	public static final Color STATUS_TEXT_COLOUR = 		new Color(0xffffff);
	public static final Color CONFIRM_TEXT_COLOUR = 	new Color(0x7FFF43);
	public static final Color WARNING_TEXT_COLOUR = 	new Color(0xFFB100);
	public static final Color ERROR_TEXT_COLOUR = 		new Color(0xFF4747);

	public static final String CHECK_MARK = 			"\u2714 ";
	public static final String WARNING_SYMBOL = 		"\u26a0 ";
	public static final String CROSS_SYMBOL = 			"\u274c ";

	public static final int STATUS_TEXT_SPACING = 30;
	public static final int CONTROL_PANEL_WIDTH = 240;

	// Main handlers

	/** The {@link JFileChooser} used to save and load files (images and calibration settings) from the app. */
	public final JFileChooser fileChooser = new JFileChooser();

	/** The {@link VideoFeed} object that controls the connection to the camera and the main processing sequence. */
	private final VideoFeed videoFeed;

	/** The capturing mode the application is currently in. */
	private CaptureMode mode;

	// GUI components

	/** The {@link JFrame} object representing the application window. */
	private final JFrame jFrame;
	/** The {@link Timer} object used to update the UI at regular intervals. */
	private final Timer timer;

	/** The {@link JPanel} object representing the control panel on the left of the app window. */
	private JPanel controlPanel;
	/** A {@link JPanel} within the control panel that actually holds the buttons. This allows the control panel to
	 * have a border and an image header whilst still allowing the buttons to be arranged in a fixed grid column. */
	private JPanel buttonPanel;

	/** The {@link JPanel} object representing the status bar at the bottom of the app window. */
	private JPanel statusBar;

	/** The {@link JPanel} object representing the space that can be filled by the video feed. The video will try to
	 * fill as much of this space as possible. */
	private JPanel videoPanel;
	/** A {@link JLabel} that holds the video feed itself. This label's icon image is updated each frame to display the
	 * video feed. */
	private JLabel videoContainer;

	// Initialisation

	/** Creates a new instance of the Flexiweld demo app. */
	public FlexiweldApp(){

//		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

		int width = 1400;//screenSize.width;
		int height = 960;//screenSize.height;

		// Create and set up the window
		jFrame = new JFrame("Flexiweld Demo App");
		jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		jFrame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e){
				onResize();
			}
		});

		// Initialise the video feed object
		videoFeed = new VideoFeed(0);

		// Start in standby mode (allows the app to start even if the camera is in use)
		mode = new StandbyMode();

		// Initialise the window contents
		initPane(jFrame.getContentPane());

		// Display the window
		jFrame.pack();
		jFrame.setSize(width, height);
		jFrame.setVisible(true);

		// Set up and start the timer
		timer = new Timer(1000/FRAMERATE, e -> this.update());
		timer.setInitialDelay(50);
		timer.start();

	}

	/** Populates the given pane with the contents of the app window. */
	private void initPane(Container pane){

		if(!(pane.getLayout() instanceof BorderLayout)){
			pane.add(new JLabel("Container doesn't use BorderLayout!"));
			return;
		}

		// Set up the status bar
		statusBar = new JPanel();
		statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.X_AXIS));
		statusBar.setBackground(Color.DARK_GRAY);
		statusBar.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY.brighter()));

		initStatusBar();

		pane.add(statusBar, BorderLayout.SOUTH); // Add it to the window

		// Set up the control panel
		controlPanel = new JPanel();
		controlPanel.setLayout(new BorderLayout());
		controlPanel.setBackground(Color.DARK_GRAY);
		controlPanel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY.brighter()));

		// Get the logo file
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream inputStream = classloader.getResourceAsStream("assets/images/logo.png");

		try{
			if(inputStream != null){
				// Read the logo image from the file
				BufferedImage logo = ImageIO.read(inputStream);
				// Scale the logo to fit the control panel
				Image logoScaled = logo.getScaledInstance(CONTROL_PANEL_WIDTH, logo.getHeight() * CONTROL_PANEL_WIDTH/logo.getWidth(), Image.SCALE_SMOOTH);
				JLabel logoLabel = new JLabel(new ImageIcon(logoScaled));
				controlPanel.add(logoLabel, BorderLayout.NORTH); // Add it to the control panel
			}
		}catch(IOException e){
			e.printStackTrace();
		}

		// Set up the button panel
		buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(12, 1, 5, 5));
		buttonPanel.setBackground(CONTROLS_BG_COLOUR);
		buttonPanel.setPreferredSize(new Dimension(CONTROL_PANEL_WIDTH, 0)); // Height is irrelevant
		buttonPanel.setBorder(BorderFactory.createLineBorder(CONTROLS_BG_COLOUR, 5));

		initButtonPanel();

		controlPanel.add(buttonPanel, BorderLayout.CENTER); // Add it to the control panel

		pane.add(controlPanel, BorderLayout.WEST); // Add the control panel to the window

		// Set up the video panel
		videoPanel = new JPanel();
		videoPanel.setLayout(new BorderLayout());
		videoPanel.setBackground(Color.DARK_GRAY);

		// Set up the video container
		videoContainer = new JLabel((ImageIcon)null); // Will be initialised later
		videoContainer.setVerticalTextPosition(SwingConstants.CENTER);
		videoContainer.setFont(BUTTON_FONT);
		videoContainer.setForeground(Color.WHITE);

		videoPanel.add(videoContainer, BorderLayout.CENTER); // Add it to the video panel

		pane.add(videoPanel, BorderLayout.CENTER); // Add the video panel to the window

	}

	/** Initialises (or re-initialises) the contents of the status bar. */
	private void initStatusBar(){

		statusBar.removeAll();

		statusBar.add(Box.createHorizontalStrut(5));

		// Use a list so modes can't modify the status bar itself
		List<Component> components = new ArrayList<>();
		mode.populateStatusBar(components);
		components.forEach(statusBar::add);
	}

	/** Initialises (or re-initialises) the contents of the button panel. */
	private void initButtonPanel(){

		buttonPanel.removeAll();

		// Use a list so modes can't modify the control panel itself
		List<Component> components = new ArrayList<>();
		mode.populateControls(this, components);
		components.forEach(buttonPanel::add);
	}

	// Setters and getters

	/** Returns the app's main {@link JFrame}. */
	public JFrame getFrame(){
		return jFrame;
	}

	/** Returns the app's {@link VideoFeed} instance. */
	public VideoFeed getVideoFeed(){
		return videoFeed;
	}

	/** Sets the current capture mode for the application and updates the interface accordingly. */
	public void setMode(CaptureMode mode){
		this.mode = mode;
		initStatusBar();
		initButtonPanel();
	}

	/**
	 * Starts or stops the video feed. If the video feed is running, stops the video feed, releases the camera and puts
	 * the app into standby mode. If the video feed is not running, attempts to access the camera and puts the app into
	 * measurement mode if successful, or displays an error message to the user if unsuccessful.
	 */
	public void toggleCamera(){
		if(videoFeed.isRunning()){
			videoFeed.stop();
			setMode(new StandbyMode());
		}else{
			videoContainer.setText("Opening camera...");
			if(videoFeed.start()){
				videoFeed.fit(videoPanel.getSize().width, videoPanel.getSize().height);
				setMode(new MeasurementMode());
			}else{
				initStatusBar();
				List<Component> components = new ArrayList<>();
				addErrorText("Unable to open camera", components);
				components.forEach(statusBar::add);
				JOptionPane.showMessageDialog(jFrame, "Cannot open camera, try closing other applications",
						"Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	// Callbacks

	/** Called when the window is resized to re-fit the video feed to the space available. */
	private void onResize(){
		if(videoFeed.isRunning()){
			videoFeed.fit(videoPanel.getSize().width, videoPanel.getSize().height);
		}
	}

	/** Called by the timer to update the window contents. */
	private void update(){

		if(videoFeed.isRunning()){
			videoContainer.setIcon(new ImageIcon(videoFeed.update(mode)));
			videoContainer.setText(null);
		}else{
			videoContainer.setIcon(null);
			videoContainer.setText("Press Start to start the live video feed");
		}

		jFrame.repaint();

	}

	// Utilities

	/**
	 * Displays a save dialog, adding a default extension to the filename if necessary, and warns the user if an
	 * existing file will be overwritten, with the option to go back and change the filename.
	 * @param filter The filename filter to use. The first extension in this filter will be the default extension.
	 * @return The resulting file path, or null if the user cancelled the operation at any stage
	 * @throws IllegalArgumentException if the given filter specifies no file extensions
	 */
	public String saveWithOverwriteWarning(FileNameExtensionFilter filter){

		if(filter.getExtensions().length == 0) throw new IllegalArgumentException("At least one file extension must be specified");

		fileChooser.setFileFilter(filter);

		int result = fileChooser.showSaveDialog(jFrame);

			if(result == JFileChooser.APPROVE_OPTION){

			File file = fileChooser.getSelectedFile();
			String name = file.getName();
			String path = file.getPath();

			if(Arrays.stream(filter.getExtensions()).noneMatch(s -> file.getName().endsWith(s))){
				path = path + "." + filter.getExtensions()[0];
				name = name + "." + filter.getExtensions()[0];
			}

			if(file.exists()){
				int choice = JOptionPane.showOptionDialog(jFrame, String.format("%s already exists, would you like to replace it?", name),
						"Confirm replace", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
						new String[]{"Replace", "Cancel"}, "Cancel");
				// Don't know if recursion is the best way of doing it but it works for our purposes
				if(choice != JOptionPane.OK_OPTION) return saveWithOverwriteWarning(filter);
			}

			return path;
		}

		return null;
	}

	/**
	 * Helper method to create a new {@link JButton} with the standard style for the control panel. Neatens up the code.
	 * @param text The text to display on the button
	 * @param listener The callback to execute when the button is pressed
	 * @return the resulting {@code JButton}
	 */
	public static JButton createButton(String text, ActionListener listener){
		JButton button = new JButton(text);
		button.setFont(BUTTON_FONT);
		button.setBackground(BUTTON_BG_COLOUR);
		button.setForeground(BUTTON_FG_COLOUR);
		button.addActionListener(listener);
		return button;
	}

	/**
	 * Helper method to create a new {@link JToggleButton} with the standard style for the control panel. Neatens up
	 * the code.
	 * @param text The text to display on the button
	 * @param listener The callback to execute when the button is pressed
	 * @return the resulting {@code JToggleButton}
	 */
	public static JToggleButton createToggleButton(String text, ActionListener listener){
		JToggleButton button = new JToggleButton(text);
		button.setFont(BUTTON_FONT);
		button.setBackground(BUTTON_BG_COLOUR);
		button.setForeground(BUTTON_FG_COLOUR);
		button.addActionListener(listener);
		return button;
	}

	/**
	 * Helper method to create a new {@link JButton} with the standard style for the control panel. Neatens up
	 * the code. This version is for on/off controls, where the button text should change when clicked.
	 * @param text The text to display on the button
	 * @param listener The callback to execute when the button is pressed
	 * @return the resulting {@code JButton}
	 */
	public static JButton createFancyToggleButton(String text, Color color, boolean startActivated, ActionListener listener){
		JButton button = new JButton(startActivated ? text + " on" : text + " off");
		button.setFont(BUTTON_FONT);
		button.setBackground(BUTTON_BG_COLOUR);
		button.setForeground(startActivated ? color : color.darker().darker());
		button.addActionListener(listener);
		button.addActionListener(e -> {
			boolean activated = button.getText().endsWith("on"); // Yuck
			button.setForeground(activated ? color.darker().darker() : color);
			button.setText(activated ? text + " off" : text + " on");
		});
		return button;
	}

	/**
	 * Helper method to add a new {@link JLabel} with the standard style for the status bar, followed by the
	 * standard-width space between status bar elements. Neatens up the code.
	 * @param text The text to display
	 * @return the resulting {@code JLabel}
	 */
	public static JLabel addStatusText(String text, List<Component> components){
		JLabel label = new JLabel(text);
		label.setFont(STATUS_BAR_FONT);
		label.setForeground(STATUS_TEXT_COLOUR);
		components.add(label);
		components.add(Box.createHorizontalStrut(STATUS_TEXT_SPACING));
		return label;
	}

	/**
	 * Helper method to add a new {@link JLabel} with the standard style for the status bar, followed by the
	 * standard-width space between status bar elements. This version also adds a check mark to the start of the text
	 * and colours the text green.
	 * @param text The text to display
	 * @return the resulting {@code JLabel}
	 */
	public static JLabel addConfirmText(String text, List<Component> components){
		JLabel label = addStatusText(CHECK_MARK + text, components);
		label.setForeground(CONFIRM_TEXT_COLOUR);
		return label;
	}

	/**
	 * Helper method to add a new {@link JLabel} with the standard style for the status bar, followed by the
	 * standard-width space between status bar elements. This version also adds a warning symbol to the start of the
	 * text and colours the text orange.
	 * @param text The text to display
	 * @return the resulting {@code JLabel}
	 */
	public static JLabel addWarningText(String text, List<Component> components){
		JLabel label = addStatusText(WARNING_SYMBOL + text, components);
		label.setForeground(WARNING_TEXT_COLOUR);
		return label;
	}

	/**
	 * Helper method to add a new {@link JLabel} with the standard style for the status bar, followed by the
	 * standard-width space between status bar elements. This version also adds a cross symbol to the start of the
	 * text and colours the text red.
	 * @param text The text to display
	 * @return the resulting {@code JLabel}
	 */
	public static JLabel addErrorText(String text, List<Component> components){
		JLabel label = addStatusText(CROSS_SYMBOL + text, components);
		label.setForeground(ERROR_TEXT_COLOUR);
		return label;
	}

	/** The main method, called directly from the command line when the program first starts. Loads the library and
	 * creates the single {@code FlexiweldApp} instance. */
	public static void main(String[] args){
		// Change the buttons to the style of the operating system (not used in the end)
//		try{
//			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//		}catch(ClassNotFoundException | InstantiationException | UnsupportedLookAndFeelException | IllegalAccessException e){
//			e.printStackTrace();
//		}
		// Load the native OpenCV library
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		// Schedule a job for the event dispatch thread:
		// creating and showing this application's GUI.
		SwingUtilities.invokeLater(FlexiweldApp::new);
	}

}