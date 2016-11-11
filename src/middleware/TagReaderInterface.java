package middleware;

public interface TagReaderInterface {
	public void startReading();
	
	public void stopReading();
	
	public Boolean connect();
	
	public Boolean closeConnection();
	
	public Boolean isRunning();
	
	public void addEventSetTagAFI(String uid, String afi);
	
	public void detectCurrentTags();
}