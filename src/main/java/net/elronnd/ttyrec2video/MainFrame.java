/*
 */

package net.elronnd.ttyrec2video;

import java.awt.RenderingHints;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.cli.Options;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;


/**
 * The application's main frame.
 */
@SuppressWarnings("serial")
public class MainFrame {
	public MainFrame(InputStreamable in, String out, int size, boolean shouldexit) {
		System.out.print("Loading...");
		// set no file to be open
		currentSource = null;

		if (in == null) return;

		openSourceFromInputStreamable(in);

		if ((currentSource.getTtyrec() == null)) {
			System.out.println("\nUnknown error loading file.  Exiting.");
			System.exit(2);
		}

		while (currentSource.backportDecodeProgress() == 0) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				System.out.println("\nInterrupted.  Exiting.");
				System.exit(3);
			}
		}

		while (currentSource.backportDecodeProgress() < currentSource.getTtyrec().getFrameCount()) {
			System.out.print(".");
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.out.println("\nInterrupted.  Exiting.");
				System.exit(3);
			}
		}

		System.out.println("done!");

		System.out.print("Saving...");

		new SaveVideo(currentSource.getTtyrec(), out, size);
		System.out.println("done!");

		if (shouldexit) {
			System.exit(0);
		}
	}

	private void unloadFile() {
		if (getCurrentSource() != null) getCurrentSource().completeCancel();
		currentSource = null;
		VDUBuffer.resetCaches();
	}

	private void openSourceFromInputStreamable(InputStreamable iStream) {
		unloadFile();
		currentSource = new InputStreamTtyrecSource(iStream);
		getCurrentSource().completeUnpause();
		getCurrentSource().addDecodeListener(() -> {});
		getCurrentSource().start();
		previousFrameIndex = 0;
	}

	private TtyrecSource currentSource;

	private int previousFrameIndex = -1;

	public double getMaximumTime() {
		if (getCurrentTtyrec() == null) return 0.0;
		return getCurrentTtyrec().getLength();
	}
	private TtyrecFrame getCurrentFrame() {
		try {
			return getCurrentTtyrec().getFrameAtIndex(previousFrameIndex);
		} catch(IndexOutOfBoundsException ex) {
			return null;
		}
	}

	/**
	 * Searches for a given string in the currently open ttyrec; if it's found,
	 * then seeks the current ttyrec to the frame where it was found.
	 * @param searchFor The string to search for.
	 * @param searchForward Whether to search forwards (true) or backwards (false).
	 * @param regex Whether the string to search for is actually a regex.
	 * @param ignoreCase Whether to do a case-insensitive (true) or case-sensitive (false) search.
	 * @param wrapAround Whether to restart the search at one end of the ttyrec if it's finished at the other end.
	 * @return A string that can be displayed to the user, summarising the results of the search.
	 */
	public String searchInTtyrec(String searchFor, boolean searchForward,
			boolean regex, boolean ignoreCase, boolean wrapAround) {
		Pattern p;
		try {
			// Regex.LITERAL would be nice, but it's too new. So we quote the
			// regex by hand, according to Perl 5 quoting rules; all letters
			// and all digits are left as-is, other characters are preceded by
			// a backslash.
			if (!regex) {
				StringBuilder sb = new StringBuilder();
				for (char c: searchFor.toCharArray()) {
					if (!Character.isLetter(c) && !Character.isDigit(c))
						sb.append('\\');
					sb.append(c);
				}
				searchFor = sb.toString();
			}
			p = Pattern.compile(searchFor, (ignoreCase ? Pattern.CASE_INSENSITIVE : 0));
		} catch (PatternSyntaxException e) {
			return "Invalid regular expression.";
		}
		for (int i = previousFrameIndex;
				i < getCurrentTtyrec().getFrameCount() && i >= 0; i += searchForward ? 1 : -1) {
			if (i == previousFrameIndex) {
				continue;
			}
			if (getCurrentTtyrec().getFrameAtIndex(i).containsPattern(p)) {
				return "Found at frame " + i + ".";
			}
		}
		if (wrapAround) {
			for (int i = searchForward ? 0 : getCurrentTtyrec().getFrameCount() - 1;
					i != previousFrameIndex;
					i += searchForward ? 1 : -1) {
				if (getCurrentTtyrec().getFrameAtIndex(i).containsPattern(p)) {
					return "Found at frame " + i + " (wrapped).";
				}
			}
		}
		return "Match not found.";
	}

	/**
	 * Gets the currently visible ttyrec source; that's the selected source from
	 * the playlist.
	 * @return the currently selected ttyrec source.
	 */
	private TtyrecSource getCurrentSource() {
		return currentSource;
	}


	/**
	 * Returns the currently viewed ttyrec.
	 * Even if more than one ttyrec is open, only the one currently showing is
	 * returned.
	 * @return The current ttyrec, or null if there are no open ttyrecs.
	 */
	public Ttyrec getCurrentTtyrec() {
		if (getCurrentSource() == null) return null;
		return getCurrentSource().getTtyrec();
	}

	/**
	 * The main entry point for the Jettyplay application.
	 * Parses and applies the effects of command-line arguments; if the
	 * arguments did not request an immediate exit, also creates a new main
	 * window for the Jettyplay application GUI and shows it.
	 * @param args The command-line arguments to parse.
	 */
	public static void main(String[] args) throws FileNotFoundException, ParseException, IOException {
		String out = null;
		InputStreamable strim = null;

		int height = 1080;


		Options options = new Options();
		options.addOption("h", true, "Height, in pixels [1080]");
		options.addOption("in", true, "Input file");
		options.addOption("out", true, "Output file");
		options.addOption("bucket", true, "ID of an aws S3 bucket");
		options.addOption("key", true, "Key for an aws S3 object");
		options.addOption("yttitle", true, "Title for a youtube upload");
		options.addOption("ytdescr", true, "Youtube video description");
		options.addOption("yttoken", true, "Authorization token for youtube");
		options.addOption("help", false, "Show help");


		CommandLine cmd = new DefaultParser().parse(options, args);

		if (cmd.hasOption("help")) {
			System.out.println("Useage: java [-server] -jar ttyrec2video.jar [-h height] [-in input file] [-bucket s3 bucket] [-key s3 object key] -out <output file>");
			System.exit(0);
		}

		if (cmd.getOptionValue("h") != null) {
			try {
				height = Integer.parseInt(cmd.getOptionValue("h"));
			} catch (NumberFormatException e) {
				System.out.println("Height must be a number.");
				System.exit(1);
			}
		}

		boolean hasinput = cmd.hasOption("in");
		boolean hass3 = cmd.hasOption("bucket") && cmd.hasOption("key");

		if (hasinput && hass3) {
			System.out.println("Error -- can't read from both a file and s3");
		} else if (hasinput) {
			strim = new InputStreamableFileWrapper(new File(cmd.getOptionValue("in")));
		} else if (hass3) {
			strim = new InputStreamableS3(cmd.getOptionValue("bucket"), cmd.getOptionValue("key"));
		} else {
			System.out.println("No input file or s3 bucket specified.");
			System.exit(1);
		}

		out = cmd.hasOption("out") ? cmd.getOptionValue("out") : cmd.hasOption("in") ? cmd.getOptionValue("in") + ".avi" : "out.avi";

		boolean hasyt = cmd.hasOption("yttitle") && cmd.hasOption("yttoken") && cmd.hasOption("ytdescr");

		new MainFrame(strim, out, height, !hasyt);

		if (hasyt) {
			new YoutubeUpload(new File(out), cmd.getOptionValue("yttoken"), cmd.getOptionValue("yttitle"), cmd.getOptionValue("ytdescr"));
		}
	}
}
