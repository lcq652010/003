package com.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertiesParser {
    
    public enum DuplicateKeyStrategy {
        ERROR, OVERWRITE, IGNORE
    }
    
    private static final Logger logger = LoggerFactory.getLogger(PropertiesParser.class);
    private static final Logger resultLogger = LoggerFactory.getLogger("RESULTS");
    
    private static final int DEFAULT_BUFFER_SIZE = 64 * 1024;
    private static final int DEFAULT_INITIAL_CAPACITY = 10000;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final int MAX_ERRORS_TO_STORE = 1000;
    private static final int MAX_WARNINGS_TO_STORE = 1000;
    
    private int totalLines = 0;
    private int validLines = 0;
    private int commentLines = 0;
    private int emptyLines = 0;
    private int totalErrors = 0;
    private int totalWarnings = 0;
    private List<String> errors = new ArrayList<>(MAX_ERRORS_TO_STORE);
    private List<String> warnings = new ArrayList<>(MAX_WARNINGS_TO_STORE);
    private Map<String, String> properties;
    private DuplicateKeyStrategy duplicateStrategy = DuplicateKeyStrategy.ERROR;
    private int bufferSize = DEFAULT_BUFFER_SIZE;
    private boolean storeProperties = true;
    
    public PropertiesParser() {
        this(DuplicateKeyStrategy.ERROR, DEFAULT_INITIAL_CAPACITY);
    }
    
    public PropertiesParser(DuplicateKeyStrategy duplicateStrategy) {
        this(duplicateStrategy, DEFAULT_INITIAL_CAPACITY);
    }
    
    public PropertiesParser(DuplicateKeyStrategy duplicateStrategy, int initialCapacity) {
        this.duplicateStrategy = duplicateStrategy;
        this.properties = new HashMap<>(initialCapacity, DEFAULT_LOAD_FACTOR);
        logger.debug("PropertiesParser initialized with strategy: {}, initialCapacity: {}", 
                     duplicateStrategy, initialCapacity);
    }
    
    public void parse(String filePath) {
        logger.info("Starting to parse file: {}", filePath);
        reset();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath), bufferSize)) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                totalLines++;
                
                if (line.isEmpty()) {
                    emptyLines++;
                    continue;
                }
                
                int start = skipLeadingWhitespace(line, 0);
                if (start >= line.length()) {
                    emptyLines++;
                    continue;
                }
                
                if (line.charAt(start) == '#') {
                    commentLines++;
                    continue;
                }
                
                int equalsIndex = indexOfNonQuoted(line, '=', start);
                if (equalsIndex == -1) {
                    totalErrors++;
                    String errorMsg = buildLineError("format error: missing equals sign", lineNumber);
                    logger.error(errorMsg);
                    if (errors.size() < MAX_ERRORS_TO_STORE) {
                        errors.add(errorMsg);
                    }
                    continue;
                }
                
                int keyEnd = skipTrailingWhitespaceBackward(line, equalsIndex - 1);
                if (keyEnd < start) {
                    totalErrors++;
                    String errorMsg = buildLineError("format error: key cannot be empty", lineNumber);
                    logger.error(errorMsg);
                    if (errors.size() < MAX_ERRORS_TO_STORE) {
                        errors.add(errorMsg);
                    }
                    continue;
                }
                
                int valueStart = skipLeadingWhitespace(line, equalsIndex + 1);
                int valueEnd = line.length() - 1;
                
                if (valueStart > valueEnd) {
                    totalErrors++;
                    String key = extractSubstring(line, start, keyEnd);
                    String errorMsg = buildKeyError("value is empty", lineNumber, key);
                    logger.error(errorMsg);
                    if (errors.size() < MAX_ERRORS_TO_STORE) {
                        errors.add(errorMsg);
                    }
                    continue;
                }
                
                String key = extractSubstring(line, start, keyEnd);
                String value = extractSubstring(line, valueStart, valueEnd);
                
                if (properties.containsKey(key)) {
                    handleDuplicateKey(key, value, lineNumber);
                } else {
                    if (storeProperties) {
                        properties.put(key, value);
                    }
                    validLines++;
                    logger.debug("Parsed property: {} = {}", key, value);
                }
            }
            
            logger.info("File parsing completed. Total lines: {}, Valid lines: {}, Errors: {}, Warnings: {}",
                       totalLines, validLines, totalErrors, totalWarnings);
            
        } catch (IOException e) {
            totalErrors++;
            String errorMsg = "Error reading file: " + e.getMessage();
            logger.error(errorMsg, e);
            if (errors.size() < MAX_ERRORS_TO_STORE) {
                errors.add(errorMsg);
            }
        }
    }
    
    private void handleDuplicateKey(String key, String value, int lineNumber) {
        totalWarnings++;
        
        switch (duplicateStrategy) {
            case ERROR:
                totalErrors++;
                totalWarnings--;
                String errorMsg = buildKeyError("duplicate key", lineNumber, key);
                logger.error(errorMsg);
                if (errors.size() < MAX_ERRORS_TO_STORE) {
                    errors.add(errorMsg);
                }
                break;
            case OVERWRITE:
                String overwriteMsg = buildKeyError("duplicate key - overwriting with new value", lineNumber, key);
                logger.warn(overwriteMsg);
                if (warnings.size() < MAX_WARNINGS_TO_STORE) {
                    warnings.add(overwriteMsg);
                }
                if (storeProperties) {
                    properties.put(key, value);
                }
                break;
            case IGNORE:
                String ignoreMsg = buildKeyError("duplicate key - ignoring (keeping first occurrence)", lineNumber, key);
                logger.warn(ignoreMsg);
                if (warnings.size() < MAX_WARNINGS_TO_STORE) {
                    warnings.add(ignoreMsg);
                }
                break;
        }
    }
    
    private int skipLeadingWhitespace(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return s.length();
    }
    
    private int skipTrailingWhitespaceBackward(String s, int end) {
        for (int i = end; i >= 0; i--) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
    
    private int indexOfNonQuoted(String s, char c, int start) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        
        for (int i = start; i < s.length(); i++) {
            char current = s.charAt(i);
            if (current == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (current == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (current == c && !inSingleQuote && !inDoubleQuote) {
                return i;
            }
        }
        return -1;
    }
    
    private String extractSubstring(String s, int start, int end) {
        if (start == 0 && end == s.length() - 1) {
            return s;
        }
        return new String(s.substring(start, end + 1));
    }
    
    private String buildLineError(String message, int lineNumber) {
        StringBuilder sb = new StringBuilder(64);
        sb.append("Line ").append(lineNumber).append(' ').append(message);
        return sb.toString();
    }
    
    private String buildKeyError(String message, int lineNumber, String key) {
        StringBuilder sb = new StringBuilder(64 + key.length());
        sb.append("Line ").append(lineNumber).append(' ').append(message).append(": ").append(key);
        return sb.toString();
    }
    
    private void reset() {
        logger.debug("Resetting parser state");
        totalLines = 0;
        validLines = 0;
        commentLines = 0;
        emptyLines = 0;
        totalErrors = 0;
        totalWarnings = 0;
        errors.clear();
        warnings.clear();
        if (properties != null) {
            properties.clear();
        } else {
            properties = new HashMap<>(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
        }
    }
    
    public void printResults() {
        resultLogger.info("=== Configuration File Parsing Results ===");
        resultLogger.info("Total lines: " + totalLines);
        resultLogger.info("Valid items: " + validLines);
        resultLogger.info("Comment lines: " + commentLines);
        resultLogger.info("Empty lines: " + emptyLines);
        resultLogger.info("Duplicate key strategy: " + duplicateStrategy);
        resultLogger.info("Buffer size: " + (bufferSize / 1024) + "KB");
        
        if (totalWarnings > 0) {
            resultLogger.info("");
            resultLogger.info("=== Warning Messages (Total: " + totalWarnings + ") ===");
            if (totalWarnings > MAX_WARNINGS_TO_STORE) {
                resultLogger.info("(Showing first " + MAX_WARNINGS_TO_STORE + " warnings)");
            }
            for (String warning : warnings) {
                resultLogger.info(warning);
            }
        }
        
        if (totalErrors > 0) {
            resultLogger.info("");
            resultLogger.info("=== Error Messages (Total: " + totalErrors + ") ===");
            if (totalErrors > MAX_ERRORS_TO_STORE) {
                resultLogger.info("(Showing first " + MAX_ERRORS_TO_STORE + " errors)");
            }
            for (String error : errors) {
                resultLogger.info(error);
            }
        } else if (totalWarnings == 0) {
            resultLogger.info("");
            resultLogger.info("All configuration items parsed successfully!");
        }
        
        if (storeProperties && !properties.isEmpty()) {
            resultLogger.info("");
            resultLogger.info("=== Valid Configuration Items ===");
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                resultLogger.info(entry.getKey() + " = " + entry.getValue());
            }
        } else if (!storeProperties) {
            resultLogger.info("");
            resultLogger.info("(Properties not stored - streaming mode)");
        }
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        
        String filePath = null;
        DuplicateKeyStrategy strategy = DuplicateKeyStrategy.ERROR;
        int bufferSize = DEFAULT_BUFFER_SIZE;
        int initialCapacity = DEFAULT_INITIAL_CAPACITY;
        boolean storeProperties = true;
        
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-s") || args[i].equals("--strategy")) {
                if (i + 1 < args.length) {
                    String strategyArg = args[i + 1].toUpperCase();
                    try {
                        strategy = DuplicateKeyStrategy.valueOf(strategyArg);
                        logger.info("Using duplicate key strategy: {}", strategy);
                    } catch (IllegalArgumentException e) {
                        logger.error("Invalid strategy '{}'. Valid strategies are: ERROR, OVERWRITE, IGNORE", args[i + 1]);
                        printUsage();
                        return;
                    }
                    i++;
                } else {
                    logger.error("Strategy option requires a value (ERROR, OVERWRITE, or IGNORE)");
                    printUsage();
                    return;
                }
            } else if (args[i].equals("-b") || args[i].equals("--buffer")) {
                if (i + 1 < args.length) {
                    try {
                        bufferSize = Integer.parseInt(args[i + 1]) * 1024;
                        if (bufferSize <= 0) {
                            logger.error("Buffer size must be positive");
                            printUsage();
                            return;
                        }
                        logger.info("Using buffer size: {} KB", args[i + 1]);
                    } catch (NumberFormatException e) {
                        logger.error("Invalid buffer size '{}'. Must be a positive integer (KB)", args[i + 1]);
                        printUsage();
                        return;
                    }
                    i++;
                } else {
                    logger.error("Buffer option requires a value in KB");
                    printUsage();
                    return;
                }
            } else if (args[i].equals("-c") || args[i].equals("--capacity")) {
                if (i + 1 < args.length) {
                    try {
                        initialCapacity = Integer.parseInt(args[i + 1]);
                        if (initialCapacity <= 0) {
                            logger.error("Initial capacity must be positive");
                            printUsage();
                            return;
                        }
                        logger.info("Using initial capacity: {}", initialCapacity);
                    } catch (NumberFormatException e) {
                        logger.error("Invalid initial capacity '{}'. Must be a positive integer", args[i + 1]);
                        printUsage();
                        return;
                    }
                    i++;
                } else {
                    logger.error("Capacity option requires a value");
                    printUsage();
                    return;
                }
            } else if (args[i].equals("--no-store")) {
                storeProperties = false;
                logger.info("Using streaming mode (properties will not be stored)");
            } else {
                filePath = args[i];
            }
        }
        
        if (filePath == null) {
            logger.error("No configuration file path specified");
            printUsage();
            return;
        }
        
        logger.info("Starting PropertiesParser with file: {}", filePath);
        
        PropertiesParser parser = new PropertiesParser(strategy, initialCapacity);
        parser.setBufferSize(bufferSize);
        parser.setStoreProperties(storeProperties);
        parser.parse(filePath);
        parser.printResults();
        
        if (parser.getTotalErrors() > 0) {
            logger.info("Parsing completed with {} errors", parser.getTotalErrors());
            System.exit(1);
        } else {
            logger.info("Parsing completed successfully");
            System.exit(0);
        }
    }
    
    private static void printUsage() {
        resultLogger.info("Usage: java PropertiesParser [options] <config_file_path>");
        resultLogger.info("Options:");
        resultLogger.info("  -s, --strategy <strategy>   Specify duplicate key handling strategy");
        resultLogger.info("                              Available strategies:");
        resultLogger.info("                                ERROR   - Report error and skip (default)");
        resultLogger.info("                                OVERWRITE - Use later value, show warning");
        resultLogger.info("                                IGNORE  - Keep first value, show warning");
        resultLogger.info("  -b, --buffer <size_KB>      Set buffer size in KB (default: 64)");
        resultLogger.info("                              Larger buffer improves performance for big files");
        resultLogger.info("  -c, --capacity <size>       Set initial HashMap capacity (default: 10000)");
        resultLogger.info("                              Set to estimated number of valid items");
        resultLogger.info("  --no-store                   Do not store property values in memory");
        resultLogger.info("                              Use for streaming-only parsing to save memory");
        resultLogger.info("Example:");
        resultLogger.info("  java PropertiesParser config.properties");
        resultLogger.info("  java PropertiesParser -s OVERWRITE -b 256 config.properties");
        resultLogger.info("  java PropertiesParser -s IGNORE -c 50000 config.properties");
        resultLogger.info("  java PropertiesParser --no-store big_file.properties");
    }
    
    public int getTotalLines() {
        return totalLines;
    }
    
    public int getValidLines() {
        return validLines;
    }
    
    public int getCommentLines() {
        return commentLines;
    }
    
    public int getEmptyLines() {
        return emptyLines;
    }
    
    public int getTotalErrors() {
        return totalErrors;
    }
    
    public int getTotalWarnings() {
        return totalWarnings;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public List<String> getWarnings() {
        return warnings;
    }
    
    public DuplicateKeyStrategy getDuplicateStrategy() {
        return duplicateStrategy;
    }
    
    public void setDuplicateStrategy(DuplicateKeyStrategy strategy) {
        this.duplicateStrategy = strategy;
    }
    
    public int getBufferSize() {
        return bufferSize;
    }
    
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }
    
    public boolean isStoreProperties() {
        return storeProperties;
    }
    
    public void setStoreProperties(boolean storeProperties) {
        this.storeProperties = storeProperties;
    }
    
    public Map<String, String> getProperties() {
        return properties;
    }
}
