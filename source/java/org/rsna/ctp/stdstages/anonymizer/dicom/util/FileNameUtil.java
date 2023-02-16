package org.rsna.ctp.stdstages.anonymizer.dicom.util;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Tags;


public class FileNameUtil {
    static final Logger LOGGER = Logger.getLogger(FileNameUtil.class);

	static final Pattern TAG_PATTERN = Pattern.compile("\\%(.*?)\\%");

	// Create a lookup map of DICOM tag name(lowerase) and the int value associated with it.
    public static final Map<String, Integer> TAGS_BY_NAME = new HashMap<> ();
	static {
		try {
            Field[] fields = Tags.class.getDeclaredFields();
			for (int i =0; i < fields.length; i++) {
				fields[i].setAccessible(true);
				try {
					if (fields[i].getGenericType().equals(Integer.TYPE)) 
						TAGS_BY_NAME.put(fields[i].getName().toLowerCase(), (Integer)fields[i].get(null));

				} catch (Exception e) {
					//DO NOTHING
				}
			}
        } catch (Exception e) {
            LOGGER.warn("Couldn't create map of tags. This error can be ignored if out file formatting is not required", e);
        }
	}

	/**
	 * Create the folders and output file based on the pattern supplied
	 * 
	 * @param dataset - DICOM dataset to save
	 * @param outPattern  - FileName format to use
	 * @param outFile - Out file created before
	 * @return - output file created matching to supplied pattern
	 */
    public static File generateOutputFileWithDicomDir(final Dataset dataset, final String outPattern, File outFile) {
		File parentFolder = outFile.getParentFile();

		// Look for the folder hierarchy first, replace with the tag values found and create the folder hierarchy 
		StringTokenizer formatTokens = new StringTokenizer(outPattern, "/");
		int totalTokenCnt = formatTokens.countTokens();
		int tokenCnt = 1;
		while(formatTokens.hasMoreTokens() && tokenCnt < totalTokenCnt) {
			String folderNamePattern = formatTokens.nextToken().toLowerCase().trim();
			System.out.println("Folder pattern:" + folderNamePattern);
			if (!folderNamePattern.isEmpty()) {
				String folderName = matchAndReplace(dataset, folderNamePattern);
				if (folderName != null && !folderName.isEmpty()) {
					parentFolder = new File(parentFolder, folderName);
					if (!parentFolder.exists() || !parentFolder.isFile()) {
						parentFolder.mkdir();
						LOGGER.info(String.format("Created folder :%s", parentFolder.getName()));
					}
				}
			}
			tokenCnt++;
		}

		// Use the pattern to create filename, and then create the file
		String fileNamePattern = formatTokens.nextToken().toLowerCase().trim();
		System.out.println("file pattern:" + fileNamePattern);
		String outfileName = matchAndReplace(dataset, fileNamePattern);
		System.out.println("outfileName:" + outfileName);
		if (outfileName != null && !outfileName.isEmpty()) {
			outFile = new File(parentFolder, outfileName);
			LOGGER.info(String.format("Created output file :%s", outFile.getName()));
			return outFile;
		}
		return null;
	}

	/**
     * @param dataset - DICOM dataset to read tag values from
     * @param outPattern - outPattern to search tags and replace them with thier values found in the dataset, supports formats %patientId%-%patientName% 
     * @return - replacement string created using the supplied pattern
     */
	public static String matchAndReplace(Dataset dataset, String outPattern) {
		Map<String, String> replacements = new HashMap<> ();
		Matcher matcher = TAG_PATTERN.matcher(outPattern);
		while (matcher.find()) {
            String key = matcher.group(1);
			// normalize the key found in the outPattern
			String tagName = key.trim().toLowerCase();
			if (TAGS_BY_NAME.containsKey(tagName)) {
				try {
					String tagValue = dataset.getString(TAGS_BY_NAME.get(tagName));
					replacements.put("%" + key + "%", (tagValue != null) ? tagValue.trim():"");
				} catch (Exception e) {
					LOGGER.warn(String.format("Unable to get tag:%s", tagName));
				}
			}
        }
		for (String key : replacements.keySet())
			outPattern = outPattern.replaceAll(key, replacements.get(key));
		return outPattern;
	}
}
