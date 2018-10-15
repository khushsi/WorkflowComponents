package edu.cmu.pslc.learnsphere.transform.TextConverter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.json.CDL;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.cmu.pslc.datashop.workflows.AbstractComponent;
import edu.cmu.pslc.datashop.workflows.ThreadedStreamReader;

public class TextConverterMain extends AbstractComponent {
    static private String OUTPUT_FILE_NAME = "convertedData";
    static private String XML_FILE_TYPE = "XML";
    static private String TAB_DELIM_FILE_TYPE = "tab-delimited";
    static private String CSV_FILE_TYPE = "CSV";
    static private String JS_FILE_TYPE = "JSON";
    public static void main(String[] args) {

        TextConverterMain tool = new TextConverterMain();
        tool.startComponent(args);
    }

    public TextConverterMain() {
        super();
    }

    @Override
    protected void runComponent() {
        String ift = this.getOptionAsString("ift");
        String oft = this.getOptionAsString("oft");
        String inputFilePath = this.getAttachment(0, 0).getAbsolutePath();
        File inputFile = this.getAttachment(0, 0);

        //output file
        File generatedFile = null;

        if (ift.equals(JS_FILE_TYPE)) {
        	if (oft.equals(CSV_FILE_TYPE) || oft.equals(TAB_DELIM_FILE_TYPE)) {

        		generatedFile = convertJsonToDelimited(inputFilePath, ift, oft);

        	} else if (oft.equals(XML_FILE_TYPE)) {

        		generatedFile = jsonToXmlFile(inputFilePath);

        	}
        } else if (ift.equals(XML_FILE_TYPE)) {
        	if (oft.equals(CSV_FILE_TYPE) || oft.equals(TAB_DELIM_FILE_TYPE)) {

        		File jsonFile = xmlToJsonFile(inputFilePath);
        		generatedFile= convertJsonToDelimited(jsonFile.getAbsolutePath(), ift, oft);

        	} else if (oft.equals(JS_FILE_TYPE)) {

        		generatedFile = xmlToJsonFile(inputFilePath);

        	}
        } else if (ift.equals(CSV_FILE_TYPE) || ift.equals(TAB_DELIM_FILE_TYPE)) {
        	if (oft.equals(XML_FILE_TYPE)) { // -k \"Some Column Key\"

        		File jsonFile = csvToJsonFile(inputFilePath, ift);
        		generatedFile = jsonToXmlFile(jsonFile.getAbsolutePath());

        	} else if (oft.equals(JS_FILE_TYPE)) {

        		generatedFile = csvToJsonFile(inputFilePath, ift);

        	} else if (oft.equals(CSV_FILE_TYPE) || oft.equals(TAB_DELIM_FILE_TYPE)) {
        		generatedFile = convertTabAndCsv(inputFile, ift, oft);
        	}
        }

        if (generatedFile != null && generatedFile.exists()) {
            Integer nodeIndex = 0;
            Integer fileIndex = 0;
            String fileLabel = oft;
            this.addOutputFile(generatedFile, nodeIndex, fileIndex, fileLabel);
        }

        System.out.println(this.getOutput());
        return;
    }

	private File csvToJsonFile(String inputFilePath, String ift) {

		String osName = System.getProperty("os.name").toLowerCase();

		this.loadBuildProperties("build.properties");

		String csvToJsonExecutable =
			System.getProperty("component.program.path");
		String intermediateFile = "convertedJson";
		File convertedFile = this.createFile(intermediateFile, ".js");

		ArrayList<String> processParams = new ArrayList<String>();
        ProcessBuilder processBuilder = new ProcessBuilder();
        Process process = null;

        processParams.add(csvToJsonExecutable);
        processParams.add("-d");

        if (osName.indexOf("win") >= 0) {
        	if (ift.equals(TAB_DELIM_FILE_TYPE)) {
	        	processParams.add("\"\t\"");
	        } else if (ift.equals(CSV_FILE_TYPE)) {
	        	processParams.add("\",\"");
	        }
        } else {
	        if (ift.equals(TAB_DELIM_FILE_TYPE)) {
	        	processParams.add("\t");
	        } else if (ift.equals(CSV_FILE_TYPE)) {
	        	processParams.add(",");
	        }
        }

        processParams.add("-i");
        processParams.add("2");
        processParams.add(inputFilePath);

        processBuilder.directory(new File(this.getComponentOutputDir()));

        processBuilder.command(processParams);
        try {

			process = processBuilder.start();

			List<String> inputLines = null;
	        List<String> errorLines = null;
			ThreadedStreamReader inputReader =
				new ThreadedStreamReader(process.getInputStream());
			ThreadedStreamReader errorReader =
				new ThreadedStreamReader(process.getErrorStream());

            Thread inputReaderThread = new Thread(inputReader);
            Thread errorReaderThread = new Thread(errorReader);

            inputReaderThread.start();
            errorReaderThread.start();

            inputReaderThread.join();
            errorReaderThread.join();

            inputLines = inputReader.getStringBuffer();
            errorLines = errorReader.getStringBuffer();
            if (!errorLines.isEmpty()) {
                errorMessages.addAll(errorLines);
            } else {
            	FileWriter fw = new FileWriter(convertedFile);
            	BufferedWriter bw = new BufferedWriter(fw);

            	for (String inputLine : inputLines) {
					bw.write(inputLine);
            	}

				bw.flush();
				bw.close();
            }
		} catch (IOException e) {
			this.addErrorMessage("Could not execute csvjson: "
				+ e.toString());
		} catch (InterruptedException e) {
			this.addErrorMessage("Could not read output stream: "
					+ e.toString());
		}
        return convertedFile;
	}

	private File xmlToJsonFile(String inputFilePath) {

		File inputFile = new File(inputFilePath);
		File convertedFile = null;
		try {

			BufferedReader br = null;
			BufferedWriter bw = null;
			String intermediateFile = "convertedJson";
			convertedFile = this.createFile(intermediateFile, ".js");
			convertedFile.createNewFile();

			if (inputFile != null && inputFile.exists()) {
				br = new BufferedReader(new FileReader(inputFile));
				bw = new BufferedWriter(new FileWriter(convertedFile));

				StringBuffer sBuffer = new StringBuffer();
				while (br.ready()) {
					sBuffer.append(br.readLine());
				}

				JSONObject tmp = null;;
				try {
					tmp = org.json.XML.toJSONObject(sBuffer.toString());
				} catch (JSONException e) {
					this.addErrorMessage("Could not convert XML to json: "
						+ e.toString());
				}

				if (tmp != null) {
					bw.write(tmp.toString());
				}

				br.close();
				bw.flush();
				bw.close();
			} else {
				addErrorMessage("Input file is null or does not exist.");
			}
		} catch (IOException e) {
			addErrorMessage("Error reading or writing out to file: " + e.toString());
		}

		return convertedFile;
	}

	private File jsonToXmlFile(String inputFilePath) {

		File inputFile = new File(inputFilePath);
		File convertedFile = null;
		try {

			BufferedReader br = null;
			BufferedWriter bw = null;

			String intermediateFile = "convertedXml";
			convertedFile = this.createFile(intermediateFile, ".xml");
			convertedFile.createNewFile();

			if (inputFile != null && inputFile.exists()) {
				br = new BufferedReader(new FileReader(inputFile));
				bw = new BufferedWriter(new FileWriter(convertedFile));

				StringBuffer sBuffer = new StringBuffer();
				while (br.ready()) {
					sBuffer.append(br.readLine());
				}

				JSONArray jsonArray = null;;

				try {
					jsonArray = new JSONArray(sBuffer.toString());
				} catch (JSONException e) {
					this.addErrorMessage("Could not convert XML to json: "
						+ e.toString());
				}

				if (jsonArray != null) {
					for (int i = 0; i < jsonArray.length(); i++) {
					    try {
					    	JSONObject jsonObj = jsonArray.getJSONObject(i);
		                	String xmlOutput = org.json.XML.toString(jsonObj);
		                	if (xmlOutput != null) {
		                		bw.write(xmlOutput);
		    					if (br.ready()) {
		    						bw.write("\n");
		    					}
		    				}

		                } catch (JSONException e) {
		                    this.addErrorMessage("Error converting workflow to XML.");
		                } catch (UnsupportedEncodingException e) {
		                    this.addErrorMessage("Unsupported encoding.");
		                } catch (IOException e) {
		                    this.addErrorMessage("Error opening workflow.");
		                }

					}

				}


				br.close();
				bw.flush();
				bw.close();
			} else {
				addErrorMessage("Input file is null or does not exist.");
			}
		} catch (IOException e) {
			addErrorMessage("Error reading or writing out to file: " + e.toString());
		}

		return convertedFile;
	}
	private File convertTabAndCsv(File inputFile, String inFileType, String outFileType) {
        File convertedFile = null;

        String fromSeparator = null;
        String toSeparator = null;

        if (inFileType.equals(TAB_DELIM_FILE_TYPE)) {
            fromSeparator = "\t";
        } else if (inFileType.equals(CSV_FILE_TYPE)) {
            fromSeparator = ",";
        } else {
            addErrorMessage("Unrecognized input file type: " + inFileType);
        }

        if (outFileType.equals(TAB_DELIM_FILE_TYPE)) {
            toSeparator = "\t";
        } else if (outFileType.equals(CSV_FILE_TYPE)) {
            toSeparator = ",";
        } else {
            addErrorMessage("Unrecognized output file type: " + outFileType);
        }

        try {

            BufferedReader br = null;
            BufferedWriter bw = null;
            String intermediateFile = "convertedDelimited";
            convertedFile = this.createFile(intermediateFile, ".txt");
            convertedFile.createNewFile();

            if (inputFile != null && inputFile.exists()) {
                br = new BufferedReader(new FileReader(inputFile));
                bw = new BufferedWriter(new FileWriter(convertedFile));

                int numHeaders = -1; int lineNumber = 1;
                while (br.ready()) {
                    String line = br.readLine();
                    String [] lineTokens = line.split(fromSeparator);

                    if (numHeaders < 0) {
                        numHeaders = lineTokens.length;
                    }

                    // If the number of values in this line is not equal to the number of headers
                    // AND it is not the last line in the file, return an error
                    if (lineTokens.length != numHeaders && br.ready()) {
                        addErrorMessage("Error in line number " + lineNumber
                                     + ".  Fewer values (" + lineTokens.length
                                     + ") in this row than the header (" + numHeaders + ").");
                        return null;
                    } else if (lineTokens.length != numHeaders && !br.ready()) {
                    	// This is the last line
                    	lineTokens = new String [0];
                    }

                    StringBuilder builder = new StringBuilder();
                    for (String s : lineTokens) {
                        if (s.contains(toSeparator)) {
                            addErrorMessage("Value \"" + s + "\" on line " + lineNumber + " contains the"
                                         + " desired separator of the output file.");
                            return null;
                        }
                        builder.append(s + toSeparator);
                    }
                    String newLine = builder.toString();

                    bw.write(newLine);
                    if (br.ready()) {
                        bw.write("\n");
                    }

                    lineNumber++;
                }
                br.close();
                bw.flush();
                bw.close();
            } else {
                addErrorMessage("Input file is null or does not exist.");
            }
        } catch (IOException e) {
            addErrorMessage("Error reading or writing out to file: " + e.toString());
        }

        return convertedFile;
    }

    private File convertJsonToDelimited(String inputFilePathName, String ift, String oft) {
        logger.info("Converting xml file: " + inputFilePathName);
        String intermediateFile = "convertedJsonToCsv";

        File tempFile = this.createFile(intermediateFile + "_csv", ".txt");
        File filePointer = null;

        if (!oft.equals(TAB_DELIM_FILE_TYPE) && !oft.equals(CSV_FILE_TYPE)) {
        	addErrorMessage("Output separator was not readable: " + oft);
        } else {

	        BufferedReader br = null;
	        BufferedWriter bw = null;

	        File inputFile = new File(inputFilePathName);
	        if (inputFile != null && inputFile.exists()) {
				try {
					br = new BufferedReader(new FileReader(inputFile));
					bw = new BufferedWriter(new FileWriter(tempFile));

					StringBuffer sBuffer = new StringBuffer();
					while (br.ready()) {
						sBuffer.append(br.readLine());
					}

					JSONArray jsonArray = null;;

					try {
						if (!sBuffer.toString().matches("\\s*\\[.*\\]\\s*")) {
							if (!sBuffer.toString().matches("\\s*\\{.*\\}\\s*")) {
								this.addErrorMessage("Not a valid JSON Array or JSON Object."
									+ sBuffer.toString());
							} else {
								jsonArray = new JSONArray("[ " + sBuffer.toString() + " ]");
							}
						} else {
							jsonArray = new JSONArray(sBuffer.toString());
						}

					} catch (JSONException e) {
						this.addErrorMessage("Could not convert XML to delimited: "
							+ e.toString());
					}

					if (jsonArray != null) {
				        String csv = CDL.toString(jsonArray);
				        bw.write(csv);
					}

					bw.flush();

					bw.close();
				} catch (IOException e) {
					this.addErrorMessage("Could not read/write XML to delimited: "
							+ e.toString());
				} catch (JSONException e) {
					this.addErrorMessage("JSONException converting XML to delimited: "
							+ e.toString());
				}


				if (tempFile.exists()) {
					if (oft.equals(TAB_DELIM_FILE_TYPE)) {
						filePointer = convertTabAndCsv(tempFile, ift, oft);
					} else if (oft.equals(CSV_FILE_TYPE)) {
						filePointer = tempFile;
					}
				}
	    	}
        }
        return filePointer;
    }

    /**
     * Look for a file named build.properties in the current directory, if
     * it exists, and load properties from it.
     * @param propsFileName the properties file name
     */
    private void loadBuildProperties(String propsFileName) {
        String filename = this.getToolDir() + propsFileName;
        File propsFile = new File(filename);

        if (propsFile.exists()) {
            try {
                System.getProperties().load(new FileInputStream(filename));
                logger.trace("System properties: ");

                for (Object propertyKey : System.getProperties().keySet()) {
                    logger.trace("\t" + propertyKey + " = " + System.getProperty((String) propertyKey));
                }


            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
}
