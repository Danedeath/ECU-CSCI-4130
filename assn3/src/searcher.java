/**
 * Authors: Carson, Adam
 *          Lamm, William
 *          Sellars, Walter
 *
 * Date: 04 - 04 - 2019
 * Course: CSCI 4130 001
 * Description: Assignment will demonstrate expertise in developing a search solution
 *              using Lucene, which is an Information Retrieval (IR) library.
 *
 */

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.BytesRef;
import org.jbibtex.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;

public class searcher {

   private static Analyzer analyzer;                     // analyzer to use

   private static String dir, corpus;                    // directory location and corpus location
   private static IndexSearcher indexSearcher;           // used to query the index
   private static IndexWriterConfig.OpenMode openMode;   // create/append/overwrite


   private static boolean trace = false;                 // display behind the scenes info

   private static ScoreDoc[] scores;                     // document scores from a query
   private static final int MAXDOCS = 10;                // maximum number of documents to display when querying
   private static int numHits;                           // numbers of hits from scores


   public static void main(String[] args) {


      /* Default Values */
      openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND; // set the writer config to create
      dir      = getDir() + "/index.lucene";                  // index directory
      corpus   = "cs-bibliography.bib";                       // corpus file

      argCheck(args);
      analyzer = new StandardAnalyzer();                      // init the analyzer with the standard one

      IndexWriter indexWriter;                                // used to write to the index
      IndexWriterConfig writerConfig;                         // config options for the writer
      IndexReader indexReader;                                // used to read the index

      try { // if something happens here we are doomed...

         // the directory of the index
         Directory directory = SimpleFSDirectory.open(Paths.get(dir)); // open a File System directory for the index

         // .CREATE_OR_APPEND != .CREATE OR .APPEND
         if (!DirectoryReader.indexExists(directory) ||
                 openMode == IndexWriterConfig.OpenMode.CREATE || openMode == IndexWriterConfig.OpenMode.APPEND) {

            writerConfig = new IndexWriterConfig(analyzer);           // use the analyzer for index creation
            writerConfig.setOpenMode(openMode);                       // set the mode of the writer

            indexWriter = new IndexWriter(directory, writerConfig);   // create the writer
            indexWriter.addDocuments(getDocs());                      // get the documents in the corpus
            indexWriter.commit();                                     // commit documents to the writer
            indexWriter.close();                                      // writer is no longer needed, close it
         }

         // reader object to read index
         indexReader   = DirectoryReader.open(directory);             // create indexReader for the searcher
         indexSearcher = new IndexSearcher(indexReader);              // create searcher for the index

         getInput();                                                  // get queries from user

      } catch (Exception e) { e.getStackTrace(); }                    // error found, display it
   }

   /**
    * argCheck will check the arguments for the program
    * @param args the array of arguments
    */
   private static void argCheck(String[] args) {
      if (args.length == 0) { usage(); }

      for (int i = 0; i < args.length; i++) {

         if (args[i].equals("-f") && new File(args[i+1]).exists()) { corpus = args[i+1]; i++; }
         if (args[i].equals("-i") && Files.isDirectory(Paths.get(args[i + 1]))) { dir = args[i+1]; i++; }
         if (args[i].equals("-o")) openMode = IndexWriterConfig.OpenMode.CREATE;
         if (args[i].equals("-a")) openMode = IndexWriterConfig.OpenMode.APPEND;
         if (args[i].equals("-r")) openMode = null;
      }

   }

   /**
    * usage() will display the accepted parameters that are completely optional
    */
   private static void usage() {
      printerr("Accepted parameters:\n" +
              "\t-f <corupus-file>, will set the corpus file location, default is cs-bibliography.bib\n" +
              "\t-i <index-directory>, will set the index directory, default is ./index.lucene\n" +
              "\t-o, will set the IndexWriterConfig.OpenMode to CREATE, default is CREATE_OR_APPEND\n" +
              "\t-a, will set the IndexWriterConfig.OpenMode to APPEND\n" +
              "\t-r, will set the IndexWriterConfig.OpenMode to null, making the program read only");
   }

   /**
    * getDocs will parse a .bib file for a list of documents in a corpus.
    * @return a list of documents
    */
   private static ArrayList<Document> getDocs() {

      // list of documents
      ArrayList<Document> list = new ArrayList<>();

      try {

         // get the contents of the corpus file and filter it
         Reader reader = new BufferedReader(new FileReader(corpus));
         CharacterFilterReader filter = new CharacterFilterReader(reader);

         // Create a new BibTex parser object, and create a database by reading the corpus file.
         //   Overriding the methods below to allow for the parser to be able to parse the .bib file
         //   given for the assignment as it fails to parse due to cross references.
         BibTeXParser parser = new BibTeXParser(){
            @Override
            public void checkStringResolution(Key key, BibTeXString string){}
            @Override
            public void checkCrossReferenceResolution(Key key, BibTeXEntry entry){}
         };

         BibTeXDatabase db = parser.parse(filter);        // parse the filtered contents of the corpus
         Map<Key, BibTeXEntry> map = db.getEntries();     // create a map of all the entries in the database

         for (BibTeXEntry entry : map.values()) {         // iterate through all the values in the database

            Document item = new Document();               // document object for each entry

            boolean foundAuth = false, foundEdit = false; // see if an editor or an author was found

            // add the type and the key to the document as fields
            item.add(new TextField("type", entry.getType().getValue(), Field.Store.YES));

            // create a map of all the fields for an entry
            Map<Key, Value> fields = entry.getFields();

            // iterate through each of the fields in the map, to populate a document's fields
            for (Key key : fields.keySet()) {

               // tokenize and get the term vector positions from the abstract
               if (key.getValue().equals("abstract")) {

                  FieldType field = new FieldType();
                  field.setStored(true);
                  field.setTokenized(true);
                  field.setStoreTermVectors(true);
                  field.setStoreTermVectorPositions(true);
                  field.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
                  item.add(new Field(key.getValue(), fields.get(key).toUserString(), field));

               }
               else if (key.getValue().equals("author")) { // the author field requires more detail

                  foundAuth = true;
                  // get the value of the author field and check for multiple authors
                  String authorString = fields.get(key).toUserString();
                  if (authorString.contains(" and ")) {

                     String[] authorList = authorString.split("and");
                     for (String auth: authorList) {    // iterate through each of the authors and add them
                        item.add(new TextField(key.getValue(), auth, Field.Store.YES));
                     }

                  } else item.add(new TextField(key.getValue(), authorString, Field.Store.YES));
               }
               else {
                   if (key.getValue().equals("editor")) { foundEdit = true; } // see if an editor was found
                   item.add(new TextField(key.getValue(), fields.get(key).toUserString(), Field.Store.YES));
               }
            }

            String value;      // initialize the string value
            if (foundAuth) {   // author has been found, use it to gen a key

               value = genKey( item.getField("author").stringValue(),  // first author's name
                               item.getField("title").stringValue(),   // title of the document
                               item.getField("year").stringValue()     // year of publication/release
                             );

               item.add(new TextField("key", value, Field.Store.YES)); // add key to document

            } else if (foundEdit) { // no author found, but editor was, use it to gen key

               value = genKey( item.getField("editor").stringValue(),  // first editor's name
                               item.getField("title").stringValue(),   // title of the document
                               item.getField("year").stringValue()     // year of publication/release
                             );

               item.add(new TextField("key", value, Field.Store.YES)); // add key to document
            }

            list.add(item); // add a document to the list of documents
         }
      } catch (Exception e) { e.getStackTrace(); }  // something went wrong, display stack trace

      // there are no documents in the index, running the program is useless
      if (list.size() == 0) { println("Index creation failed..."); System.exit(1); }

      return list;
   }

   /**
    * genKey will (re)generate a key for a bibtex document
    * @param author author of the document, sometimes is an editor
    * @param title  title of the document
    * @param year   release year of the document
    * @return a key value in the format <AUTH/EDITOR>-<YEAR>-<TITLE>
    */
   private static String genKey(String author, String title, String year) {

      title = title.contains(":") ? title.substring(0,title.indexOf(":")) : title; // if a title has a ':' use everything before that

      String[] key = {
              author.split(author.contains(",") ? "," : " ")[0],       // last name of the author
              year,                                                    // year of publication/release
              String.join("-", title.split(" "))        // title of the document
      };

      return String.join("-", key);                           // format: authorLastName-Year-Title
   }

   /**
    * getInput() will continually get input from the user and process queries
    */
   private static void getInput() {

      Scanner kyb  = new Scanner(System.in);                   // scanner object...
      String input;                                            // input holder

      getHelp();                                               // display helpful information
      while(true) {                                            // continually get user input until !quit command

         System.out.print("Query: "); input = kyb.nextLine();  // get the query information

         if (input.charAt(0) == '!')                           // check for command given
            if (commands(input)) { continue; } else { break; } // false if quit requested, otherwise continue

         processQuery(input.split(" "));                // split the input and process it
      }
   }

   /**
    * processQuery will do some small steps in the overall query process. Afterwards it will call the main query method
    * @param input an array of the split input by spaces
    */
   private static void processQuery(String[] input) {

      String type = input[0].toLowerCase();                                        // get the type of query
      if (input.length > 1) {                                                      // matchall doesnt require info

         String[] holder = Arrays.copyOfRange(input, 1, input.length);       // remove the type from the query info
         query(type, holder);                                                      // send the processed information
      } else { query(type, null); }                                          // matchall doesnt require info
   }

   /**
    * generalQuery is a helper function of processQuery, it will process a basic query
    * @param input a query split by spaces
    */
   private static void query(String type, String[] input) {

      try {

         String field = null, qString = null;
         Query query;

         if (input != null && !type.equals("matchall")) { // general error check

            field = input[0];
            qString = String.join(" ", Arrays.copyOfRange(input, 1, input.length));
            if (trace) println("Type:: " + type + "\nQuery::" + qString + "\nField::" + field );
         }

         if (trace && type.equals("matchall")) println("Query::" + type); // display some information

         if (type.equals("disjunction") || type.equals("termrange")) { // these two queries require more work

            query = specialGenQuery(type, field, qString);             // use specialGenQuery to get generate our query
         } else { query = genQuery(type, field, qString); }            // generate the query object

         if (query != null) {                                          // general queries can be null (failure to parse)

            TopDocs topDocs = indexSearcher.search(query, MAXDOCS);    // get the top 10 documents
            scores = topDocs.scoreDocs;                                // get the document scores

            // find out the actual size we will be working with
            int size = Math.min(MAXDOCS, Math.toIntExact(topDocs.totalHits));
            numHits  = (int)topDocs.totalHits;

            printScores(size, type);                                   // display the results of the query

         } else { println("Something went wrong during parsing..."); } // there was an issue parsing the general queries...

      } catch (Exception e) { e.printStackTrace(); }                   // general try/catch for errors
   }

   /**
    * specialGenQuery is a helper method to query, it will perform the actions required for
    *    the term-range and disjunction queries.
    * @param type  the type of query
    * @param field the field to perform a query on
    * @param input the actual query
    * @return the specified query object
    */
   private static Query specialGenQuery(String type, String field, String input) {

      String[] items; // holder for the input, different queries have different needs
      input = input.toLowerCase();

      switch(type) {

         case ("termrange"):
            items = input.split(",");
            return new TermRangeQuery(field, new BytesRef(items[0]), new BytesRef(items[1]), true, true);

         case ("disjunction"):
            ArrayList<Query> queryCollection = new ArrayList<>();                         // create a collection of queries
            input = input.trim();                                                         // remove trailing/leading spaces.
            field = field.replace(";", "");                             // separate the fields
            String[] fields = input.substring(0, input.indexOf(";")).split(" ");    // get all of the fields
            String phrase   = "(" + input.substring(input.indexOf(";") + 1).trim() + ")";  // make a phrase for the parser

            try {

               for(String item: fields)                                                    // query each field against the phrase
                  queryCollection.add(new QueryParser(item, analyzer).parse(phrase));      // add each query to the collection

               return new DisjunctionMaxQuery(queryCollection, Float.parseFloat(field));   // return a new disjunction query

            } catch (Exception e) { e.getStackTrace(); }
      }
      return null; // something happened when querying, return null
   }

   /**
    * genQuery is a helper method for query, it will return the appropriate structure for a query based
    *    on the type type of query requested
    * @param type  type of query
    * @param field field of the query
    * @param input the actual query
    * @return query object
    */
   private static Query genQuery(String type, String field, String input) {

      input = input.toLowerCase();

      switch(type) {

         case("term"):        return new TermQuery(new Term(field, input));
         case("wildcard"):    return new WildcardQuery(new Term(field, input));
         case("prefix"):      return new PrefixQuery(new Term(field, input));
         case("fuzzy"):       return new FuzzyQuery(new Term(field, input));
         case("regex"):       return new RegexpQuery(new Term(field, input));
         case("matchall"):    return new MatchAllDocsQuery();
         case("phrase"):      return genQuery(field, input);
         case("multiphrase"): return genQuery(field, input);
         case("boolean"):     return genQuery(field, input);
         default:             println("Invalid query type given..."); return null;
      }
   }

   /**
    * genQuery is an overloaded version of the above method, it will process general queries that require
    *   parsing (phrase, boolean, and multiphrase)
    * @param field field to query
    * @param input the actual query
    * @return query object
    */
   private static Query genQuery(String field, String input) {

      try {

         QueryParser qParser = new QueryParser(field, analyzer);
         return qParser.parse(input);
      } catch(Exception e) { println(e.getMessage()); }

      return null;
   }

   /**
    * printScores will display the scores of documents from a query
    * @param size the size of scores list
    * @param type  type of query
    */
   private static void printScores(int size, String type) {

      if (scores.length == 0) { println("No results..."); return; }  // no results, dont want errors
      println("Results for " + type + " query:");                    // display the query type

      for (int i = 0; i < size; i++) {                               // display upto MAXDOCS

         String out = String.format("\tDoc: %-7d   Score: %.3f",
                                    scores[i].doc, scores[i].score); // display document info
         println(out);
      }

      println("\tTotal hits: " + numHits + "\n");                    // display total number of hits
   }

   /**
    * commands is a helper function for query, it will return false for a quit command,
    *    otherwise it will process a single command.
    * @param input the input from the user
    * @return false if quit command, otherwise true
    */
   private static boolean commands(String input) {

      String cmd = input.split(" ")[0].toLowerCase();

      switch (cmd) {

         case ("!quit"):      return false;                       // exit command
         case ("!help"):      getHelp(); break;                   // display commands available
         case ("!queryhelp"): getQueryHelp(); break;              // separated due to size
         case ("!trace"):                                         // turn tracing on/off
            trace = !trace;
            println("Tracing " + (trace ? "enabled...\n" : "disabled...\n")); break;

         default: println("'" + cmd + "' is an invalid command"); // reached an invalid command
      }

      return true;
   }

   /**
    * getHelp will display the available options for user input
    */
   private static void getHelp() {

      String out =
              "Available options:\n" +
              "\t!quit:  will stop the program\n" +
              "\t!help:  will display the available options\n" +
              "\t!trace: will enable/disable tracing\n" +
              "\t!queryHelp: will display useful information about querying \n";
      println(out);
   }

   /**
    * getQueryHelp will display information about the query system
    */
   private static void getQueryHelp() {
      String out =
              "Querying:\n" +
              "\tboolean query: 'boolean abstract +recent +years -have'\n" +
              "\t\twill perform a query on the abstract fields that have 'recent' and 'years' but not 'have'\n" +
              "\n\tdisjunction query: disjunction 5; abstract title; distributed computing\n" +
              "\t\twill perform a disjunction query with the following conditions:\n" +
              "\t\t\tMultiplier: 5\n" +
              "\t\t\tFields: abstract title\n" +
              "\t\t\tPhrase: distributed computing\n" +
              "\n\tmatchall query: 'matchall'\n" +
              "\t\twill perform a match all documents query\n" +
              "\n\tmultiphrase query: 'multiphrase abstract (distributed computing) and (recent years)'\n" +
              "\t\twill perform a query on the phrases 'distributed computing' and 'recent years'\n" +
              "\n\tterm query: 'term abstract have'\n" +
              "\t\twill perform a query on the abstract fields\n" +
              "\n\tphrase query: 'phrase abstract (recent years)'\n" +
              "\t\twill perform a query on 'recent years' on the abstract fields\n" +
              "\n\tterm range query: 'termrange year 2015,2017\n" +
              "\t\twill perform a query on the year field that are between 2015 and 2017 inclusively. ranges are separated by ','\n" +
              "\n\tregex query: 'regex abstract \\w+'\n" +
              "\t\twill perform a query on all alphanumeric characters in the abstract fields\n" +
              "\n\tprefix query: 'prefix abstract stu'\n" +
              "\t\twill perform a query on all words in the abstract that begin with stu\n" +
              "\n\twildcard query: 'wildcard year 201*'\n" +
              "\t\twill perform a query on years that are within the range 2010 to 2019\n";
      println(out);
   }

   /**
    * getDir will return the working directory
    * @return the working directory
    */
   private static String getDir() { return System.getProperty("user.dir"); }

   /**
    * print and println are print functions that will print any object with a toString method
    * @param m object to print
    */
   private static void println(String m)  { System.out.println(m); }
   private static void printerr(String m) { System.err.println(m); }
}