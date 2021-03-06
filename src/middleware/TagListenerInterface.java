package middleware;

import java.util.ArrayList;

/**
 * TagListenerInterface.
 * 
 * Describes the interface for events on a tag-reader.
 */
public interface TagListenerInterface {
	/**
	 * A tag has been detected.
	 * 
	 * @param bibTag
	 */
	public void tagDetected(BibTag bibTag);

	/**
	 * A tag has been removed.
	 * 
	 * @param bibTag
	 */
	public void tagRemoved(BibTag bibTag);
	
	/**
	 * Lists tags currently on device.
	 * 
	 * @param bibTags
	 */
	public void tagsDetected(ArrayList<BibTag> bibTags);
	
	/**
	 * A tag's AFI has been set.
	 * 
	 * @param bibTag
	 */
	public void tagAFISet(BibTag bibTag, boolean success);
	
	/**
	 * The device is processing new tags.
	 */
	public void processingNewTags();
}
