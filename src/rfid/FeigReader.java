package rfid;

import java.util.ArrayList;

import de.feig.FeIscListener;
import de.feig.FePortDriverException;
import de.feig.FeReaderDriverException;
import de.feig.FedmException;
import de.feig.FedmIscReader;
import de.feig.FedmIscReaderConst;
import de.feig.FedmIscReaderID;

public class FeigReader extends Thread implements FeIscListener, TagReaderInterface {
	private FedmIscReader fedm;

	private LoggerImpl logger;
	private TagListenerInterface tagListener;
	private Boolean debug;

	private Boolean connected = false;
	private Boolean running = false;
	private Boolean detectCurrentTags = false;
	private ArrayList<BibTag> bibTags = new ArrayList<BibTag>();
	private ArrayList<BibTag> currentTags = new ArrayList<BibTag>();
	private ArrayList<EventSetAFI> eventsSetAFI = new ArrayList<EventSetAFI>();

	/**
	 * Constructor.
	 * 
	 * @param logger
	 *   The logger implementation.
	 * @param tagListener
	 *   The tag listener where reader events are passed to.
	 */
	public FeigReader(LoggerImpl logger, TagListenerInterface tagListener, Boolean debug) {
		this.logger = logger;
		this.tagListener = tagListener;
		this.debug = debug;
	}	

	/**
	 * Initialize FeigReader
	 * 
	 * @return Boolean
	 */
	private Boolean initiateFeigReader() {
		// Initiate the reader object.
		try {
			fedm = new FedmIscReader();
		} catch (Exception ex) {
			// @TODO: Handle.
			ex.printStackTrace();
			logger.log("Error message: " + ex.getMessage() + "\n" + ex.toString());
			return false;
		}

		if (fedm == null) {
			return false;
		}

		// Set the table size of the reader.
		// As of now it has been set to 20
		// which means the reader can read an inventory of max 20.
		// The reader will therefore work best when 20 tags on reader.
		try {
			fedm.setTableSize(FedmIscReaderConst.ISO_TABLE, 20);
			return true;
		} catch (FedmException ex) {
			ex.printStackTrace();
			logger.log("Error code: " + ex.getErrorcode() + "\n" + ex.getStackTrace());
		}
		return false;
	}
	
	/**
	 * Open USB port.
	 * 
	 * @throws FeReaderDriverException 
	 * @throws FePortDriverException 
	 * @throws FedmException 
	 */
	private void openUSBPort() throws FedmException, FePortDriverException, FeReaderDriverException {
		// Close connection if any has already been established.
		closeConnection();

		// Connect to USB.
		fedm.connectUSB(0);
		fedm.addEventListener(this, FeIscListener.SEND_STRING_EVENT);
		fedm.addEventListener(this, FeIscListener.RECEIVE_STRING_EVENT);

		// Read important reader properties and set reader type in reader object.
		fedm.readReaderInfo();
	}

	/**
	 * Close connection to FeigReader.
	 * 
	 * @throws FedmException 
	 * @throws FeReaderDriverException 
	 * @throws FePortDriverException 
	 */
	private void closeConnection() throws FedmException, FePortDriverException, FeReaderDriverException {
		// Close connection if there is any.
		if (fedm.isConnected()) {
			fedm.removeEventListener(this, FeIscListener.SEND_STRING_EVENT);
			fedm.removeEventListener(this, FeIscListener.RECEIVE_STRING_EVENT);
			fedm.disConnect();
		}
	}
	
	@Override
	public void onReceiveProtocol(FedmIscReader arg0, String arg1) {}

	@Override
	public void onReceiveProtocol(FedmIscReader arg0, byte[] arg1) {}

	@Override
	public void onSendProtocol(FedmIscReader arg0, String arg1) {}

	@Override
	public void onSendProtocol(FedmIscReader arg0, byte[] arg1) {}

	/**
	 * Get the MID.
	 * 
	 * @param id
	 *   @TODO: What is this id?
	 * 
	 * @return
	 */
	private String getMIDFromMultipleBlocks(String id) {
		// This method uses readSpecific block
		// to read four blocks which is enough
		// to get the MID on a bib tag.
		// Then the MID is created and returned.
		String b0 = readSpecificBlock(id, (byte) 0);
		String b1 = readSpecificBlock(id, (byte) 1);
		String b2 = readSpecificBlock(id, (byte) 2);
		String b3 = readSpecificBlock(id, (byte) 3);
		String mid = b0 + b1 + b2 + b3;

		try{
            String midSub = mid.substring(6, 26);
            String midReplaced = midSub.replaceAll(".(.)?", "$1");
            String s = mid.substring(0, 6) + midReplaced;
            return s;
        } catch(Exception ex){
        	// Ignore.
            ex.printStackTrace();
            logger.log("Could not create MID: " + mid);
        }
	
		return "";
	}

	/**
	 * Read block.
	 * 
	 * This method is used when reading a specific block (dbAddress).
	 * 
	 * @param id
	 *   @TODO
	 * @param dbAddress
	 *   The block address.
	 *   
	 * @return String
	 *   @TODO
	 */
	private String readSpecificBlock(String id, byte dbAddress) {
		String dataBlockString = null;

		fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_REQ_UID, id);
		fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_CMD, (byte) 0x23);
		fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_MODE, (byte) 0x00);
		fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_MODE_ADR, (byte) 0x01);
		fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_REQ_DBN, (byte) 0x01);
		fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_REQ_DB_ADR, dbAddress);

		try {
			fedm.sendProtocol((byte) 0xB0);

			int idx = fedm.findTableIndex(0, FedmIscReaderConst.ISO_TABLE, FedmIscReaderConst.DATA_SNR, id);
			if (idx < 0) {
				return "";
			}
			dataBlockString = fedm.getStringTableData(idx, FedmIscReaderConst.ISO_TABLE, FedmIscReaderConst.DATA_RxDB, dbAddress);

			return dataBlockString;
		} catch (FePortDriverException ex) {
			ex.printStackTrace();
			logger.log("Error code: " + ex.getErrorCode() + "\n" + ex.toString());
		} catch (FeReaderDriverException ex) {
			ex.printStackTrace();
			logger.log("Error code: " + ex.getErrorCode() + "\n" + ex.toString());
		} catch (FedmException ex) {
			ex.printStackTrace();
			logger.log("Error code: " + ex.getErrorcode() + "\n" + ex.toString());
		}
		return "";
	}

	/**
	 * Write block.
	 * 
	 * @param data
	 * @param id
	 * @param dbAddress
	 */
	private void writeSpecificBlock(String data, String id, byte dbAddress) {
		// This is method is used to write
		// any string data to a specific block
		// on a tag(dbAddress). This is used in the
		// writeMIDToMultipleBlocks method.

		byte[] dataBlock = null;
		dataBlock = getByteArrayFromString(data);

		try {
			int idx = fedm.findTableIndex(0, FedmIscReaderConst.ISO_TABLE, FedmIscReaderConst.DATA_SNR, id);
			if (idx < 0) {
				return;
			}
			fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_REQ_UID, id);
			fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_CMD, (byte) 0x24);
			fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_MODE, (byte) 0x00);
			fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_MODE_ADR, (byte) 0x01);
			fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_REQ_DBN, (byte) 0x01);
			fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_REQ_DB_ADR, dbAddress);

			fedm.setTableData(idx, FedmIscReaderConst.ISO_TABLE, FedmIscReaderConst.DATA_BLOCK_SIZE, (byte) 0x04);
			fedm.setTableData(idx, FedmIscReaderConst.ISO_TABLE, FedmIscReaderConst.DATA_TxDB, dbAddress, dataBlock);

			fedm.sendProtocol((byte) 0xB0);

		} catch (FedmException ex) {
			ex.printStackTrace();
			logger.log("Error code: " + ex.getErrorcode() + "\n" + ex.toString());
		} catch (FePortDriverException ex) {
			ex.printStackTrace();
			logger.log("Error code: " + ex.getErrorCode() + "\n" + ex.toString());
		} catch (FeReaderDriverException ex) {
			ex.printStackTrace();
			logger.log("Error code: " + ex.getErrorCode() + "\n" + ex.toString());
		}
	}

	/**
	 * Get Byte array from string
	 * 
	 * @param large
	 * @return
	 */
	public byte[] getByteArrayFromString(String large) {
		// this method can be used to retrieve a
		// byte array from any string.
		byte[] bytes = new byte[large.length() / 2];

		int before = 0;
		int after = 2;

		for (int i = 0; i < large.length() / 2; i++) {
			Byte current = Byte.valueOf(large.substring(before, after));
			bytes[i] = current;
			before += 2;
			after += 2;
		}
		return bytes;
	}

	/**
	 * Write MID.
	 * 
	 * @param id
	 */
	public void writeMIDToMultipleBlocks(String id, String midDec) {
		// this method uses writeSpecific block
		// to write the MID to tags. The MID fills
		// up multiple blocks, which is why we must
		// split up the MID into blocks and the
		// write to each specific block

		String m1 = midDec.substring(0, 6);
		String m2 = midDec.substring(6, midDec.length());

		ArrayList<String> mid1Ascii = new ArrayList<String>();
		ArrayList<String> mid2Ascii = new ArrayList<String>();

		for (int i = 0; i < m2.length(); i++) {
			mid2Ascii.add(m2.charAt(i) + "");
		}

		mid2Ascii.set(0, Integer.parseInt(mid2Ascii.get(0)) + 48 + "");
		mid2Ascii.set(1, Integer.parseInt(mid2Ascii.get(1)) + 48 + "");
		mid2Ascii.set(2, Integer.parseInt(mid2Ascii.get(2)) + 48 + "");
		mid2Ascii.set(3, Integer.parseInt(mid2Ascii.get(3)) + 48 + "");
		mid2Ascii.set(4, Integer.parseInt(mid2Ascii.get(4)) + 48 + "");
		mid2Ascii.set(5, Integer.parseInt(mid2Ascii.get(5)) + 48 + "");
		mid2Ascii.set(6, Integer.parseInt(mid2Ascii.get(6)) + 48 + "");
		mid2Ascii.set(7, Integer.parseInt(mid2Ascii.get(7)) + 48 + "");
		mid2Ascii.set(8, Integer.parseInt(mid2Ascii.get(8)) + 48 + "");
		mid2Ascii.set(9, Integer.parseInt(mid2Ascii.get(9)) + 48 + "");

		mid1Ascii.add(0, Integer.parseInt(m1.substring(0, 2)) + 6 + "");
		mid1Ascii.add(1, m1.substring(2, 6));

		String x = mid1Ascii.get(0) + mid1Ascii.get(1);

		writeSpecificBlock(x + mid2Ascii.get(0), id, (byte) 0);
		writeSpecificBlock(mid2Ascii.get(1) + mid2Ascii.get(2) + mid2Ascii.get(3) + mid2Ascii.get(4), id, (byte) 1);
		writeSpecificBlock(mid2Ascii.get(5) + mid2Ascii.get(6) + mid2Ascii.get(7) + mid2Ascii.get(8), id, (byte) 2);
		writeSpecificBlock(mid2Ascii.get(9) + "000000", id, (byte) 3);
	}

	/**
	 * Write AFI.
	 * 
	 * @param id
	 * @param afi
	 */
	public Boolean writeAFI(String id, String afi) {
		// This method can be called whenever you
		// want to write the AFI on a tag.
		byte byteAfi = (byte) 0x07;
		if (afi.equals("194")) {
			byteAfi = (byte) 0xC2;
		} else if (afi.equals("7") || afi.equals("07")) {
			byteAfi = (byte) 0x07;
		}
		
		fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_REQ_UID, id);
		
		try {
			int idx = fedm.findTableIndex(0, FedmIscReaderConst.ISO_TABLE, FedmIscReaderConst.DATA_SNR, id);
			if (idx < 0) {
				return false;
			}

			fedm.setTableData(idx, FedmIscReaderConst.ISO_TABLE, FedmIscReaderConst.DATA_AFI, byteAfi);
			fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_CMD, (byte) 0x27);
			fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_MODE, (byte) 0x00);
			fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_MODE_ADR, (byte) 0x01);

			fedm.sendProtocol((byte) 0xB0);

			return true;
		} catch (Exception e) {
			e.printStackTrace();
			logger.log("Error code: " + e.getMessage() + "\n" + e.toString());
			return false;
		}
	}

	/**
	 * Get UIDs of the tags on the device.
	 * 
	 * @return
	 * @throws FedmException
	 * @throws FePortDriverException
	 * @throws FeReaderDriverException
	 */
	public String[] getUIDs() throws FedmException, FePortDriverException, FeReaderDriverException {
		fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_CMD, 0x01);
		fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_MODE, 0x00);
		fedm.deleteTable(FedmIscReaderConst.ISO_TABLE);

		fedm.sendProtocol((byte) 0x69); // RFReset
		fedm.sendProtocol((byte) 0xB0); // ISOCmd

		// @TODO - Review: Possibility of infinite loop?
		while (fedm.getLastStatus() == 0x94) { // more flag set?
			fedm.setData(FedmIscReaderID.FEDM_ISC_TMP_B0_MODE_MORE, 0x01);
			fedm.sendProtocol((byte) 0xB0);
		}

		// Get number of entries in the ISO_TABLE.
		int tableLength = fedm.getTableLength(FedmIscReaderConst.ISO_TABLE);
		
		String[] uids = new String[tableLength];
		
		// Register tags on device.
		for (int i = 0; i < tableLength; i++) {
			String UID = fedm.getStringTableData(i, FedmIscReaderConst.ISO_TABLE, FedmIscReaderConst.DATA_SNR);
			uids[i] = UID;
		}
		
		return uids;
	}
	
	/**
	 * Start the thread.
	 */
	public void run() {
		while (running) {
			try {
				String[] uids = getUIDs();

				// Clear registered tags.
				bibTags.clear();
								
				// Register tags on device.
				for (int i = 0; i < uids.length; i++) {
					bibTags.add(new BibTag(uids[i], getMIDFromMultipleBlocks(uids[i])));
				}
				
				// Compare current tags with tags detected.
				// Emit events if changes.
				for (int i = 0; i < bibTags.size(); i++) {
					Boolean contains = false;
					for (int j = 0; j < currentTags.size(); j++) {
						if (bibTags.get(i).getMID().equals(currentTags.get(j).getMID()) && 
							bibTags.get(i).getUID().equals(currentTags.get(j).getUID())) {
							contains = true;
							break;
						}
					}
					if (!contains) {
						tagListener.tagDetected(bibTags.get(i));
					}
				}
				for (int i = 0; i < currentTags.size(); i++) {
					Boolean contains = false;
					for (int j = 0; j < bibTags.size(); j++) {
						if (currentTags.get(i).getMID().equals(bibTags.get(j).getMID()) &&
							currentTags.get(i).getUID().equals(bibTags.get(j).getUID())) {
							contains = true;
							break;
						}
					}
					if (!contains) {
						tagListener.tagRemoved(currentTags.get(i));
					}
				}
				
				// Updated current tags.
				currentTags = new ArrayList<BibTag>(bibTags);

				// Process EventSetAFI events.
				ArrayList<EventSetAFI> events = new ArrayList<EventSetAFI>(eventsSetAFI);
				eventsSetAFI.clear();
				
				for (EventSetAFI event : events) {
					BibTag tag = null;
					
					// Get tag.
					for (BibTag b : bibTags) {
						if (b.getUID().equals(event.getUid())) {
							tag = b;
							
							break;
						}
					}
					
					if (tag != null) {
						// DEBUG
						if (debug) {
							System.out.println("Writing: " + event.toString());
						}

						if (writeAFI(tag.getUID(), event.getAfi())) {
							tag.setAFI(event.getAfi());
							
							tagListener.tagAFISet(tag, true);
						}
						else {
							tagListener.tagAFISet(tag, false);
						}
					} 
					else {
						logger.log("UID: " + event.getUid() + ", could not be found on reader");

						tagListener.tagAFISet(tag, false);
					}
				}
				
				// If requested current tags.
				if (detectCurrentTags) {
					tagListener.tagsDetected(currentTags);
					detectCurrentTags = false;
				}
				
				// DEBUG
				if (debug) {
					System.out.println(currentTags.toString());
				}

				// Yield. 
				Thread.sleep(50);
			} catch (Exception e) {
				logger.log("Error message: " + e.getMessage() + "\n" + e.toString());
			}
		}
	}

	@Override
	public void startReading() {
		if (!connected) {
			connect();
		}
		
		if (!running) {
			System.out.println("Start");
			running = true;
			this.start();
		}
	}

	@Override
	public void stopReading() {
		this.running = false;
	}

	@Override
	public Boolean connect() {
		// Initialize FEIG Reader.
		if (!initiateFeigReader()) {
			logger.log("FEIG Reader: Error - CANNOT INITIALIZE");
			running = false;
			return false;
		} else {
			try {
				openUSBPort();
				logger.log("USB Connection: ESTABLISHED");
			}
			catch (Exception e) {
				logger.log("USB Connection Error: " + e.getMessage());
				return false;
			}
		}
		return true;
	}

	@Override
	public Boolean disconnect() {
		try {
			closeConnection();
			running = false;
		} catch (Exception e) {
			logger.log(e.getMessage());
			return false;
		}
		
		return true;
	}

	@Override
	public Boolean isRunning() {
		return this.running;
	}

	/**
	 * Add event - set tag AFI.
	 */
	@Override
	public void addEventSetTagAFI(String uid, String afi) {
		eventsSetAFI.add(new EventSetAFI(uid, afi));
	}

	@Override
	public void detectCurrentTags() {
		detectCurrentTags = true;
	}
}
