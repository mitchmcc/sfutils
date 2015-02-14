/**
 * CSVUtils is released into the public domain, do what ya want with it.
 * By Lee Clarke
 */
public class CSVUtils {  
 
  /**   
   * Removed quotes from around imported csv values if present.
   */
  public static String stripQuotes(String strIn) {  
    def valOut = strIn;  
    if(valOut==null)  
      valOut == "";  
    if(valOut.startsWith("\""))   
      valOut = valOut.substring(1);  
    if(valOut.endsWith("\""))   
      valOut = valOut.substring(0,valOut.length()-1);  
    return valOut;  
  }
    
  /**   
   * Load the csv into a Map using indexed position of values for key and value. 
   * @param keyPos - col index of the key value in a row
   * @param valPos - pull value data from index/col position in row
   * @param csvFilePath - full path to file
   */  
  public static Map csvToMap(int keyPos, int valPos, String csvFilePath) {  
    def fp= new File(csvFilePath);  
    def rtnMap = [:];  
  
    def palines = 0;  
    fp.splitEachLine(',') {  
      if(palines > 0)//skip col header line  
      {  
        def row = [];  
                row = it;  
        def key = CSVUtils.stripQuotes( row[keyPos]);  
        def val = CSVUtils.stripQuotes( (valPos < 0)?row.last():row[valPos]);  
        rtnMap.putAt(key, val);  
      }  
      palines++  
    }  
    return rtnMap;  
  }  
  
  /**
   * loads values in csv file into multi-dimentional like List of rows and cols.
   * @param csvFilePath - full file path.
   * @param skipFirstRow - skip first row if it contains column names.
   */
  public static List csvToList( String csvFilePath, boolean skipFirstRow) {
    def fr= new File(csvFilePath);
    def rtnList = [];
    def rlines = 0;
    fr.splitEachLine(',') {
      if(!(skipFirstRow && rlines == 0)) {
        println "row= $it"
        def row = [];
        row = it;
        rtnList << row;
      }
      rlines++
    }
    return rtnList;
  }
  
  /**
   * loads values in csv file into multi-dimentional like List of rows and cols.
   * @param csvFilePath - full file path.
   */
  public static List csvToList( String csvFilePath) { 
    return csvToList(csvFilePath,true);
  }
}  