import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PropertiesParser {
    
    public enum DuplicateKeyStrategy {
        ERROR, OVERWRITE, IGNORE
    }
    
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
    }
    
    public void parse(String filePath) {
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
                    if (errors.size() < MAX_ERRORS_TO_STORE) {
                        errors.add(buildLineError("format error: missing equals sign", lineNumber));
                    }
                    continue;
                }
                
                int keyEnd = skipTrailingWhitespaceBackward(line, equalsIndex - 1);
                if (keyEnd < start) {
                    totalErrors++;
                    if (errors.size() < MAX_ERRORS_TO_STORE) {
                        errors.add(buildLineError("format error: key cannot be empty", lineNumber));
                    }
                    continue;
                }
                
                int valueStart = skipLeadingWhitespace(line, equalsIndex + 1);
                int valueEnd = line.length() - 1;
                
                if (valueStart > valueEnd) {
                    totalErrors++;
                    if (errors.size() < MAX_ERRORS_TO_STORE) {
                        String key = extractSubstring(line, start, keyEnd);
                        errors.add(buildKeyError("value is empty", lineNumber, key));
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
                }
            }
        } catch (IOException e) {
            totalErrors++;
            if (errors.size() < MAX_ERRORS_TO_STORE) {
                errors.add("Error reading file: " + e.getMessage());
            }
        }
    }
    
    private void handleDuplicateKey(String key, String value, int lineNumber) {
        totalWarnings++;
        
        switch (duplicateStrategy) {
            case ERROR:
                totalErrors++;
                totalWarnings--;
                if (errors.size() < MAX_ERRORS_TO_STORE) {
                    errors.add(buildKeyError("duplicate key", lineNumber, key));
                }
                break;
            case OVERWRITE:
                if (warnings.size() < MAX_WARNINGS_TO_STORE) {
                    warnings.add(buildKeyError("duplicate key - overwriting with new value", lineNumber, key));
                }
                if (storeProperties) {
                    properties.put(key, value);
                }
                break;
            case IGNORE:
                if (warnings.size() < MAX_WARNINGS_TO_STORE) {
                    warnings.add(buildKeyError("duplicate key - ignoring (keeping first occurrence)", lineNumber, key));
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
        System.out.println("=== Configuration File Parsing Results ===");
        System.out.println("Total lines: " + totalLines);
        System.out.println("Valid items: " + validLines);
        System.out.println("Comment lines: " + commentLines);
        System.out.println("Empty lines: " + emptyLines);
        System.out.println("Duplicate key strategy: " + duplicateStrategy);
        System.out.println("Buffer size: " + (bufferSize / 1024) + "KB");
        
        if (totalWarnings > 0) {
            System.out.println("\n=== Warning Messages (Total: " + totalWarnings + ") ===");
            if (totalWarnings > MAX_WARNINGS_TO_STORE) {
                System.out.println("(Showing first " + MAX_WARNINGS_TO_STORE + " warnings)");
            }
            for (String warning : warnings) {
                System.out.println(warning);
            }
        }
        
        if (totalErrors > 0) {
            System.out.println("\n=== Error Messages (Total: " + totalErrors + ") ===");
            if (totalErrors > MAX_ERRORS_TO_STORE) {
                System.out.println("(Showing first " + MAX_ERRORS_TO_STORE + " errors)");
            }
            for (String error : errors) {
                System.out.println(error);
            }
        } else if (totalWarnings == 0) {
            System.out.println("\nAll configuration items parsed successfully!");
        }
        
        if (storeProperties && !properties.isEmpty()) {
            System.out.println("\n=== Valid Configuration Items ===");
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                System.out.println(entry.getKey() + " = " + entry.getValue());
            }
        } else if (!storeProperties) {
            System.out.println("\n(Properties not stored - streaming mode)");
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
                    } catch (IllegalArgumentException e) {
                        System.out.println("Error: Invalid strategy '" + args[i + 1] + "'. Valid strategies are: ERROR, OVERWRITE, IGNORE");
                        printUsage();
                        return;
                    }
                    i++;
                } else {
                    System.out.println("Error: Strategy option requires a value (ERROR, OVERWRITE, or IGNORE)");
                    printUsage();
                    return;
                }
            } else if (args[i].equals("-b") || args[i].equals("--buffer")) {
                if (i + 1 < args.length) {
                    try {
                        bufferSize = Integer.parseInt(args[i + 1]) * 1024;
                        if (bufferSize <= 0) {
                            System.out.println("Error: Buffer size must be positive");
                            printUsage();
                            return;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Error: Invalid buffer size '" + args[i + 1] + "'. Must be a positive integer (KB)");
                        printUsage();
                        return;
                    }
                    i++;
                } else {
                    System.out.println("Error: Buffer option requires a value in KB");
                    printUsage();
                    return;
                }
            } else if (args[i].equals("-c") || args[i].equals("--capacity")) {
                if (i + 1 < args.length) {
                    try {
                        initialCapacity = Integer.parseInt(args[i + 1]);
                        if (initialCapacity <= 0) {
                            System.out.println("Error: Initial capacity must be positive");
                            printUsage();
                            return;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Error: Invalid initial capacity '" + args[i + 1] + "'. Must be a positive integer");
                        printUsage();
                        return;
                    }
                    i++;
                } else {
                    System.out.println("Error: Capacity option requires a value");
                    printUsage();
                    return;
                }
            } else if (args[i].equals("--no-store")) {
                storeProperties = false;
            } else {
                filePath = args[i];
            }
        }
        
        if (filePath == null) {
            System.out.println("Error: No configuration file path specified");
            printUsage();
            return;
        }
        
        PropertiesParser parser = new PropertiesParser(strategy, initialCapacity);
        parser.setBufferSize(bufferSize);
        parser.setStoreProperties(storeProperties);
        parser.parse(filePath);
        parser.printResults();
    }
    
    private static void printUsage() {
        System.out.println("Usage: java PropertiesParser [options] <config_file_path>");
        System.out.println("Options:");
        System.out.println("  -s, --strategy <strategy>   Specify duplicate key handling strategy");
        System.out.println("                              Available strategies:");
        System.out.println("                                ERROR   - Report error and skip (default)");
        System.out.println("                                OVERWRITE - Use later value, show warning");
        System.out.println("                                IGNORE  - Keep first value, show warning");
        System.out.println("  -b, --buffer <size_KB>      Set buffer size in KB (default: 64)");
        System.out.println("                              Larger buffer improves performance for big files");
        System.out.println("  -c, --capacity <size>       Set initial HashMap capacity (default: 10000)");
        System.out.println("                              Set to estimated number of valid items");
        System.out.println("  --no-store                   Do not store property values in memory");
        System.out.println("                              Use for streaming-only parsing to save memory");
        System.out.println("Example:");
        System.out.println("  java PropertiesParser config.properties");
        System.out.println("  java PropertiesParser -s OVERWRITE -b 256 config.properties");
        System.out.println("  java PropertiesParser -s IGNORE -c 50000 config.properties");
        System.out.println("  java PropertiesParser --no-store big_file.properties");
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
