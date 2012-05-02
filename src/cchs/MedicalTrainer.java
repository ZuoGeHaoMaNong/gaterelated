/**
 * 
 */
package cchs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.lucene.util.Version;


import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Distribution;
import edu.stanford.nlp.stats.IntCounter;
import gate.util.compilers.eclipse.jdt.internal.compiler.ast.ThisReference;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import stats.KLDivergence;
import tokenizers.LucenePTBTokenizer;
import tokenizers.NgramTokenizer;
import tokenizers.StopWordList;
import topicmodel.ModelContainer;
import topicmodel.MultinomialDocumentModel;
import weka.WekaInstances;
import weka.WekaRoutines;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.ClassifierType;
import weka.classifiers.CommonClassifierRoutines;
import weka.classifiers.Evaluation;
import weka.classifiers.J48Classifier;
import weka.classifiers.functions.Logistic;
import weka.classifiers.functions.SimpleLogistic;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * a set of very specific routines for my work on medical data.
 * @author ashwani
 * an example of the commandline argument to invoke this is as following
 * --mt --ifile /home/ashwani/xyz/allfeedtype.csv --tcol 3 --agecol 2 --idcol 1 
 * --lcol 7  --cltype=adaboost    --eval --lage 0.0 --uage 1.0 --idname mrn 
 * --ngrams 1 --cloptions="-W weka.classifiers.functions.pLogistic,-P 100"
 * 
 * another example is as following
 * 
 * --mt --ifile /home/ashwani/xyz/allfeedtype.csv --tcol 3 --agecol 2 
 * --idcol 1 --lcol 7   --lage 0.0 --uage 1.0 --idname=mrn --ngrams 1:2:3 
 * --dumparff=/home/ashwani/xyz/arff/biasedsettrigram.arff --dense
 *
 */
public class MedicalTrainer {
	private static Options options = new Options();
	private List<Record> population = null;
	private String[]  cloptions;
	private ClassifierType cltype;
	private boolean languageModel = false;
	private static void addOptions() {
		options.addOption("lm", "languagemodel",false, "whether to use language" +
				"modelling or the simple indicator spaces");
		options.addOption("dense", "denseinstance",false, "whether to use dense"
				+"instance or not");
		options.addOption("cloptions", "cloption",true, "option to the classifier must be included in brackets" +
				"< cl options >, also mustbe at the end of the classifer");

		options.addOption("removesinglecount", "removesinglecount", false, "Remove terms with" +
				"single count in the termspace");
		options.addOption("eval", "eval", false, "Are we doing just evaluatin? Using the weka" +
				"api Evalatuation, by default it  is set to false");
		options.addOption("dumparff", "dumparff", true, "dump the training set arff, this is" +
				"for debugging purpose");
		options.addOption("predict", "predict", false, "run the classifer built to predict and dump");
		options.addOption("cltype", "classifierType", true, "the type of weka classifier to use");
		options.addOption("ifile", "inputfile", true, "the csv file to  be oprated on");
		options.addOption("dfile", "dumpfile", true, "the csv file to which we will dump the output" +
				"of the prediction");
		options.addOption("st", "stem", true, "do we want to stem. defaults to true");
		options.addOption("lc", "lowercase", true, "do we want to lowercase, defualts to" +
				"true");
		options.addOption("stopword", "stopword", true, "do we want to use the stop words, " +
		"defaults to StopWordList.medicalWords");
		options.addOption("tcol", "textcol", true, "text column number");
		options.addOption("lcol", "labelcol", true, "label col number");
		options.addOption("agecol", "agecol", true, "age column number");
		options.addOption("lage", "lowage", true, "the low age value");
		options.addOption("uage", "upage", true, "the up age value");
		options.addOption("idcol", "idcol", true, "identifier column number");
		options.addOption("idname", "idname", true, "nameOfIdentifier");
		options.addOption("lclass", "labelclass", true, "List of Labels of the class, " +
				"currently defaults to FeedCategories");
		options.addOption("ngrams", "ngrams", true, "the ngrams to operate upon, the listofngrams" +
				"is separated by semicolon :");
		options.addOption("rmsinglecount","removesinglecount", true, "remove singlecount terms");
		options.addOption("dumpindex","indexestodump", true, "indexes which needs to be dumped" +
				"for pretty output. bydefault set to {0,1}. " +
				"Just to avoid making command line argument big");
		options.addOption("rmindex","removeindexes", true, "indexes which needs to be removed" +
				"before building the classifer. bydefault set to {0,1}. " +
				"os as to avoid making command line argument big");
	}

	public static class Record {
		private final String id;
		/**
		 * @return the text
		 */
		public String getText() {
			return text;
		}
		/**
		 * @param text the text to set
		 */
		public void setText(String text) {
			this.text = text;
		}
		/**
		 * @return the age
		 */
		public float getAge() {
			return age;
		}
		/**
		 * @param age the age to set
		 */
		public void setAge(float age) {
			this.age = age;
		}
		/**
		 * @return the textcategory
		 */
		public String getTextcategory() {
			return textcategory;
		}
		/**
		 * @param textcategory the textcategory to set
		 */
		public void setTextcategory(String textcategory) {
			this.textcategory = textcategory;
		}
		/**
		 * @return the id
		 */
		public String getId() {
			return id;
		}
		private String text;
		private float age;
		private String textcategory;
		public Record (String id) {
			this.id = id;
		}
	}
	/**
	 * launch pad for this
	 * @throws Exception
	 */
	public void launchPad() throws Exception {
		Counter<String> termspace = null;
		if (this.languageModel) {
			this.populateRecords();
			this.doLanguageModelClassification();
		}
		else {
		termspace = this.returnFreqDistOnSetOfNgrams();
		if (this.removesinglecount) 
			termspace = KLDivergence.removeSingleCounteTerms(termspace);
		this.trainingSet = this.returnIndicatorVectorOfTermSpace(termspace.keySet(),
				true);
		}
		if (this.predict) {
			this.classifier = CommonClassifierRoutines.trainOnInstances(classifier, 
					this.trainingSet, this.indicesToRemove,this.classifieroptions );
			WekaInstances testingSet= this.returnIndicatorVectorOfTermSpace(
					termspace.keySet(),	false);
			testingSet.setClassMissingForEachInstance();
		}
		else if (this.eval) 
			this.evaluate();
		
		else if (this.classifier != null && this.trainingSet != null){
			CommonClassifierRoutines.leaveOneOutCrossValidation(this.classifier, this.trainingSet, 
					this.indicesToRemove, this.indicesTodump, this.classifieroptions
					, this.dumpfile);
		}
		if(this.dumparff != null) {
			WekaRoutines.dumpArff(this.dumparff, this.trainingSet);
		}
			
	}
	
	public String[] cloptionparser(String opt) {
		StringTokenizer st  = new StringTokenizer(opt,",");
		ArrayList<String> opts = new ArrayList<String>();
		while (st.hasMoreTokens()) {
			opts.add(st.nextToken());
		}
		return opts.size() == 0 ? null : 
			opts.toArray(new String[opts.size()]);
	}
	
	public static MedicalTrainer  parserArgument(String[] commandargs) 
	throws Exception {
		
		MedicalTrainer.addOptions();
		MedicalTrainer mt = new MedicalTrainer();
		CommandLineParser cmdLineGnuParser = new GnuParser();
		CommandLine command = cmdLineGnuParser.parse(options, commandargs);
		if (command.hasOption("lm"))
			mt.languageModel = true;
		if (command.hasOption("dense"))
			mt.denseinstance = true;
		if (command.hasOption("dumparff")) {
			mt.dumparff = command.getOptionValue("dumparff");
		}
		if (command.hasOption("predict")) {
			 mt.predict = true;
		}
		if (command.hasOption("eval")) {
			mt.setEval(true);
		}
		if (command.hasOption("cltype")) {
			if (command.hasOption("cloptions")) {
				mt.cloptions = mt.cloptionparser(command.getOptionValue("cloptions"));
			}
			mt.cltype = ClassifierType.valueOf(command.getOptionValue("cltype"));
			mt.initClassifier(mt.cltype,
					mt.cloptions);
		}
		if (command.hasOption("ifile")) {
			mt.setFilename(command.getOptionValue("ifile"));
		}
		if (command.hasOption("dfile")) {
			mt.dumpfile = command.getOptionValue("dfile");
		}
		
		if (command.hasOption("st")) {
			mt.setStem(new Boolean(command.getOptionValue("st")));
		}
		
		if (command.hasOption("lc")) {
			mt.setLowercase(new Boolean(command.getOptionValue("lc")));
		}
		if (command.hasOption("stopword")) {
			throw new UnsupportedOperationException("Havn't yet implemented " +
					"the command line parsing for stop words");
		}
		
		if (command.hasOption("tcol")) {
			mt.setTextcol(Integer.parseInt(command.getOptionValue("tcol")));
		}
		
		if (command.hasOption("lcol")) {
			mt.setLabelcol(Integer.parseInt(command.getOptionValue("lcol")));
		}
		
		if (command.hasOption("agecol")) {
			mt.setAgecol(Integer.parseInt(command.getOptionValue("agecol")));
		}
		
		if (command.hasOption("lage")) {
			mt.setLowageval(Float.parseFloat(command.getOptionValue("lage")));
		}
		
		if (command.hasOption("uage")) {
			mt.setUpageval(Float.parseFloat(command.getOptionValue("uage")));
		}
		
		if (command.hasOption("idcol")) {
			mt.setIdentifierCol(Integer.parseInt(command.getOptionValue("idcol")));
		}
		
		if (command.hasOption("idname")) {
			mt.setIdentifierName(command.getOptionValue("idname"));
		}
		if (command.hasOption("ngrams")) {
			String ngrams = command.getOptionValue("ngrams");
			StringTokenizer st = new StringTokenizer(ngrams,":");
			List<Integer> ngramsToGet = new ArrayList<Integer>();
			while (st.hasMoreTokens()) {
				ngramsToGet.add(Integer.parseInt(st.nextToken()));
			}
			mt.setNgramsToGet(ngramsToGet);
		}
		
		if (command.hasOption("lclass")) {
			throw new UnsupportedOperationException("Havn't yet implemented " +
					"the command line parsing for this label classes");
		}
		return mt;
	}
	
	public void initClassifier(ClassifierType ctype, String[] options) 
	throws Exception {
		if (ctype.equals(ClassifierType.j48)) {
			this.classifier = new J48Classifier();
			this.classifier.setOptions(options);
			
		}
		else if (ctype.equals(ClassifierType.simplelogistic)) {
			this.classifier = new SimpleLogistic(100, true , false);
			this.classifier.setOptions(options);
		}
		else if (ctype.equals(ClassifierType.logistic)) {
			this.classifier = new Logistic();
			this.classifier.setOptions(options);
		}
		else if (ctype.equals(ClassifierType.adaboost)) {
			this.classifier = new AdaBoostM1();
			this.classifier.setOptions(options);
		}
	}
	
	private int numcrossfold;
	private boolean removesinglecount=true;
	private boolean denseinstance = false;
	/**
	 * Dump the training set to arff
	 */
	private String dumparff= null;
	/**
	 * file to dump the results to.
	 */
	private String dumpfile = null;
	private String[] classifieroptions =null;
	private boolean predict = false;
	private WekaInstances trainingSet = null;
	private boolean eval = false;
	
	/**
	 * @return the eval
	 */
	public boolean isEval() {
		return eval;
	}

	/**
	 * @param eval the eval to set
	 */
	public void setEval(boolean eval) {
		this.eval = eval;
	}
	private AbstractClassifier classifier=null;
    private String identifierName;
	/**
	 * @return the identifierName
	 */
	public String getIdentifierName() {
		return identifierName;
	}

	/**
	 * @param identifierName the identifierName to set
	 */
	public void setIdentifierName(String identifierName) {
		this.identifierName = identifierName;
	}

	/**
	 * @return the textcol
	 */
	public int getTextcol() {
		return textcol;
	}

	/**
	 * @param textcol the textcol to set
	 */
	public void setTextcol(int textcol) {
		this.textcol = textcol;
	}

	/**
	 * @return the identifierCol
	 */
	public int getIdentifierCol() {
		return identifierCol;
	}

	/**
	 * @param identifierCol the identifierCol to set
	 */
	public void setIdentifierCol(int identifierCol) {
		this.identifierCol = identifierCol;
	}

	/**
	 * @return the agecol
	 */
	public int getAgecol() {
		return agecol;
	}

	/**
	 * @param agecol the agecol to set
	 */
	public void setAgecol(int agecol) {
		this.agecol = agecol;
	}

	/**
	 * @return the labelcol
	 */
	public int getLabelcol() {
		return labelcol;
	}

	/**
	 * @param labelcol the labelcol to set
	 */
	public void setLabelcol(int labelcol) {
		this.labelcol = labelcol;
	}

	/**
	 * @return the lowageval
	 */
	public float getLowageval() {
		return lowageval;
	}

	/**
	 * @param lowageval the lowageval to set
	 */
	public void setLowageval(float lowageval) {
		this.lowageval = lowageval;
	}

	/**
	 * @return the upageval
	 */
	public float getUpageval() {
		return upageval;
	}

	/**
	 * @param upageval the upageval to set
	 */
	public void setUpageval(float upageval) {
		this.upageval = upageval;
	}

	/**
	 * @return the ngramsToGet
	 */
	public List<Integer> getNgramsToGet() {
		return ngramsToGet;
	}

	/**
	 * @param ngramsToGet the ngramsToGet to set
	 */
	public void setNgramsToGet(List<Integer> ngramsToGet) {
		this.ngramsToGet = ngramsToGet;
	}

	/**
	 * @return the stopwordlist
	 */
	public Set<?> getStopwordlist() {
		return stopwordlist;
	}

	/**
	 * @param stopwordlist the stopwordlist to set
	 */
	public void setStopwordlist(Set<?> stopwordlist) {
		this.stopwordlist = stopwordlist;
	}

	/**
	 * @return the stem
	 */
	public boolean isStem() {
		return stem;
	}

	/**
	 * @param stem the stem to set
	 */
	public void setStem(boolean stem) {
		this.stem = stem;
	}

	/**
	 * @return the lowercase
	 */
	public boolean isLowercase() {
		return lowercase;
	}

	/**
	 * @param lowercase the lowercase to set
	 */
	public void setLowercase(boolean lowercase) {
		this.lowercase = lowercase;
	}

	/**
	 * @return the filename
	 */
	public String getFilename() {
		return filename;
	}

	/**
	 * @param filename the filename to set
	 */
	public void setFilename(String filename) {
		this.filename = filename;
	}

	/**
	 * @return the classvalues
	 */
	public List<String> getClassvalues() {
		return classvalues;
	}

	/**
	 * @param classvalues the classvalues to set
	 */
	public void setClassvalues(List<String> classvalues) {
		this.classvalues = classvalues;
	}

	/**
	 * @return the datasetname
	 */
	public String getDatasetname() {
		return datasetname;
	}

	/**
	 * @param datasetname the datasetname to set
	 */
	public void setDatasetname(String datasetname) {
		this.datasetname = datasetname;
	}

	/**
	 * @return the skipHeader
	 */
	public boolean isSkipHeader() {
		return skipHeader;
	}

	/**
	 * @param skipHeader the skipHeader to set
	 */
	public void setSkipHeader(boolean skipHeader) {
		this.skipHeader = skipHeader;
	}
	private int[] indicesTodump = {0,1};
	private int[] indicesToRemove = {0,1};
	private int textcol;
	private int identifierCol;
	private int agecol;
	private int labelcol;
	private float lowageval;
	private float upageval;
	private List<Integer> ngramsToGet;
	private Set<?> stopwordlist = null;
	private boolean stem;
	private boolean lowercase;
	private String filename;
	List<String> classvalues;
	private String datasetname;
	private boolean skipHeader;
	public MedicalTrainer() {
		this.classvalues = FeedCategories.returnAllValues();
		stopwordlist = StopWordList.getMedicalStopWordList(Version.LUCENE_35) ;
		this.lowercase = true;
		this.stem = true;
		this.skipHeader = true;
		this.datasetname = "medicalTrainer";
	}
	
	/**
	 * 
	 * @param filename : Name of the csv file from where I am getting data.
	 * @param termspace : The term space which I need to check for text in each row
	 * in csv file.
	 * @param identifierCol : The column number of the identifier in csv file.
	 * @param identifierName : The name of identifier. (Need it to name 
	 * the attribute of weka instances)
	 * @param textcol : The column number of the text field in csv.
	 * @param agecol : The column number of age column in csv file.
	 * @param lowvalue : The lower limit of the age.
	 * @param upvalue : The upper limit of age.
	 * @param labelcol : The column number which contains the label we have
	 * assigne for the text column.
	 * @param classvalues: List of the possible label values.
	 * @param ngramsToget : ngram to get. U
	 * @param tolowercase : Do you want to convert  the input text to lower case ?
	 * @param stopwordlist : List of stop word  to be removed from the text.
	 * @param datasetname : The name of the weka instances data set.
	 * @param skipHeader: Skips the first line of the csv file.
	 * @param stem: stems the input
	 * @return : The weka Instances.
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public  WekaInstances returnIndicatorVectorOfTermSpace(Set<String> termspace,
			boolean doNotAddUnkownClass)
	throws NumberFormatException, IOException {
		int numattributes = 0;
		if (termspace != null)
			numattributes = termspace.size();
		
		if (this.identifierCol >= 0)
			numattributes++;
		if (this.agecol >= 0)
			numattributes++;
		if (this.labelcol >= 0)
			numattributes++;
		WekaInstances wekainstances = new WekaInstances(this.datasetname, new ArrayList<Attribute>(),
				numattributes);
		wekainstances.setSparseInstance(!this.denseinstance);
		int index = 0;
		if (this.identifierCol >= 0) {
			ArrayList<String> ambiguity=null;
			wekainstances.insertAttributeAt(new Attribute(this.identifierName),index++);
						
		}
		if (this.agecol >= 0) {
			wekainstances.insertAttributeAt(new Attribute("age"),index++);
		}
		
		if (termspace != null) {
			for (String s : termspace) {
				wekainstances.insertAttributeAt(new Attribute(s),index++);
				//	wekainstances.insertAttributeAt(new Attribute(s, possibleVal),index++);
			}
		}
		
		if (labelcol >= 0) {
			wekainstances.insertAttributeAt(new Attribute("class",
					classvalues),index++);
			wekainstances.setClassIndex(index -1);
		}
		CSVReader csvreader = new CSVReader(new FileReader(this.filename));
		String[] nextLine =null;
		if (this.skipHeader)
			csvreader.readNext();
		while ((nextLine = csvreader.readNext()) != null) {
			float age = (float)-1.0 ;
			if (this.agecol >= 0) {
				age = Float.parseFloat(nextLine[agecol-1]);
				if (age < this.lowageval || age > this.upageval) 
					continue;
			}
			wekainstances.initWorkingInstance();
			wekainstances.addWorkingInstance();
			if (this.identifierCol >= 0) 
				wekainstances.setValueOfWorkingInstance(this.identifierName,
						Integer.parseInt(nextLine[this.identifierCol-1]));
			if (this.agecol >= 0)
				wekainstances.setValueOfWorkingInstance("age",age);
			String text = nextLine[this.textcol-1];
			if (termspace != null) {
				for (String t : termspace) {
					wekainstances.setValueOfWorkingInstance(t, 0.0);
				}
			}
		//	System.out.println(nextLine[identifierCol-1]+" "+ age);
			for (int ngram : this.ngramsToGet) {
				Set<String> terms = this.returnUniqueNgramTermspace(text, ngram);
				if (termspace != null) {
					for (String t : terms)	{
						if (termspace.contains(t))
							wekainstances.setValueOfWorkingInstance(t, 1.0);
					}
				}
				else {
					for (String t : terms)					
						wekainstances.setValueOfWorkingInstance(t, 1.0, true);
				}
			}
			String lclass = null;
			if (this.labelcol >= 0 && nextLine.length >= this.labelcol
					&& nextLine[labelcol -1].length() > 0) {
				lclass = nextLine[this.labelcol -1];
			//	System.out.println(lclass);
				wekainstances.setValueOfWorkingInstance("class", lclass);
			}
			if ( lclass == null && doNotAddUnkownClass) {
				wekainstances.delete(wekainstances.numInstances() -1 );
			}
		}
		wekainstances.setValueForEachInstance(false, 0.0);
		return wekainstances;
	}
	
	
	public List<String> returnTokens(String input, int ngram) {
		List<String> uniqueTerms = new ArrayList<String>();
		List<String> tokens = MedicalTrainer.ptbTokenizer(input);
		if (this.lowercase)
			MedicalTrainer.toLowerCase(tokens);
		if (this.stopwordlist != null)
			MedicalTrainer.removeStopWords(tokens,this.stopwordlist);
		if (this.stem)
			MedicalTrainer.simpleStem(tokens);		
		if (this.stopwordlist != null)
			MedicalTrainer.removeStopWords(tokens,this.stopwordlist);
		
		StringBuilder sb = new StringBuilder();
		for(String tok: tokens) {
			if (tok.contains("-")) {
				StringTokenizer st  = new StringTokenizer(tok, "-");
				while (st.hasMoreTokens()) {
					sb.append(st.nextToken());
					sb.append(" ");
				}
			}
			else {
				sb.append(tok);
				sb.append(" ");
			}
			
		}
		String newstring = sb.toString().trim();
		LucenePTBTokenizer lptb = 
			new LucenePTBTokenizer(new StringReader(newstring));
		NgramTokenizer ntokenizer = new NgramTokenizer(lptb, ngram);
		ntokenizer.tokenize();
		while(ntokenizer.hasNext()) {
			uniqueTerms.add((String)ntokenizer.next());
		}
		return uniqueTerms;
	}
	
	public  HashSet<String> returnUniqueNgramTermspace(String input, int ngram ) {
		LinkedHashSet<String> uniqueTerms = new LinkedHashSet<String>();
		List<String> tokens = MedicalTrainer.ptbTokenizer(input);
		if (this.lowercase)
			MedicalTrainer.toLowerCase(tokens);
		if (this.stopwordlist != null)
			MedicalTrainer.removeStopWords(tokens,this.stopwordlist);
		if (this.stem)
			MedicalTrainer.simpleStem(tokens);		
		if (this.stopwordlist != null)
			MedicalTrainer.removeStopWords(tokens,this.stopwordlist);
		
		StringBuilder sb = new StringBuilder();
		for(String tok: tokens) {
			if (tok.contains("-")) {
				StringTokenizer st  = new StringTokenizer(tok, "-");
				while (st.hasMoreTokens()) {
					sb.append(st.nextToken());
					sb.append(" ");
				}
			}
			else {
				sb.append(tok);
				sb.append(" ");
			}
			
		}
		String newstring = sb.toString().trim();
		LucenePTBTokenizer lptb = 
			new LucenePTBTokenizer(new StringReader(newstring));
		NgramTokenizer ntokenizer = new NgramTokenizer(lptb, ngram);
		ntokenizer.tokenize();
		while(ntokenizer.hasNext()) {
			uniqueTerms.add((String)ntokenizer.next());
		}
		return uniqueTerms;
	}
	
	public static void simpleStem(List<String> input) {
		Morphology mp = new Morphology();
		for (int i = 0; i < input.size(); i++) {
			//String orig = input.get(i);
		//	System.out.println("As"+input.get(i)+"As");
			String stem = mp.stem(input.get(i));
			if (!input.get(i).equals(stem)) {
		//		System.out.println(orig+"  "+ stem);
				input.set(i, stem);
			}
		}
	}
	/**
	 * Coversts the token to lower case. remove the stop words using the medical 
	 * stop word list defined by me
	 * @param csvFile
	 * @param lowage : lowage filter
	 * @param upage : high age filter
	 * @param agecol : columne number in the csv file which contains age
	 * @param textcol : column number in the csv file which contains the text
	 * @param ngram : the ngram
	 * @return
	 */
	public  HashSet<String> returnUniqueNgramTermSpace(int ngram) {

		CSVReader csvreader;
		LinkedHashSet<String> uniqueTerms = new LinkedHashSet<String>();
		try {
			    
				csvreader = new CSVReader(new FileReader(this.filename));
				String[] nextLine =null;
				if (this.skipHeader)
					csvreader.readNext();
				while ((nextLine = csvreader.readNext()) != null) {
					Double age = Double.parseDouble(nextLine[this.agecol-1]);
					if (age < this.lowageval || age > this.upageval)
						continue;
					if (nextLine[this.textcol-1].length() <= 0)
						continue;
					List<String> tokens = MedicalTrainer.ptbTokenizer(
							nextLine[this.textcol -1]);
					MedicalTrainer.toLowerCase(tokens);
					MedicalTrainer.removeStopWords(tokens,this.stopwordlist);
					
					if (this.stem)
						MedicalTrainer.simpleStem(tokens);
					
					MedicalTrainer.removeStopWords(tokens, this.stopwordlist);
					StringBuilder sb = new StringBuilder();
					for(String tok: tokens) {
						if (tok.contains("-")) {
							StringTokenizer st  = new StringTokenizer(tok, "-");
							while (st.hasMoreTokens()) {
								sb.append(st.nextToken());
								sb.append(" ");
							}
						}
						else {
							sb.append(tok);
							sb.append(" ");
						}
					}
					String newstring = sb.toString().trim();
					LucenePTBTokenizer lptb = 
						new LucenePTBTokenizer(new StringReader(newstring));
					NgramTokenizer ntokenizer = new NgramTokenizer(lptb, ngram);
					ntokenizer.tokenize();
					while(ntokenizer.hasNext()) {
						uniqueTerms.add((String)ntokenizer.next());
					}
				}
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return uniqueTerms;
	}
	
	
	public static List<String> ptbTokenizer(String input) {
		StringReader reader = new StringReader(input);
		LucenePTBTokenizer lptb = new LucenePTBTokenizer(reader);
		List<String> result = new Vector<String>();
		while (lptb.getPtbtokenizer().hasNext()) {
			CoreLabel label = (CoreLabel) lptb.getPtbtokenizer().next();
			result.add(label.word());
		}
		return result;
	}
	
	public static List<String> toLowerCase(List<String> input) {
		for (int index = 0; index < input.size();index++) {
			input.set(index, input.get(index).toLowerCase());
		}
		return input;
	}
	
	public static List<String> removeStopWords(List<String> input, 
			Set<?> stopwords) {
		if (stopwords == null)
			return input;
		List<String> toberemoved = new Vector<String>();
		for (String s : input) {
			if (stopwords.contains(s))
				toberemoved.add(s);
				
		}
		for (String remove : toberemoved) {
			input.remove(remove);
		}
//		input.removeAll();
		return input;
	}
	

	public static Counter<String> addToCounter(Counter<String> cntr, 
			List<String> tokens) {
		for (String tok : tokens) {
			cntr.incrementCount(tok);
		}
		return cntr;
	}
	
	
	public static Counter<String> returnFreqDist(String string , int ngram,
			boolean tolowercase, boolean stem,
			Set<?> stopwordlist) {
		Counter<String> cntr = new IntCounter<String>();
		List<String> tokens = MedicalTrainer.ptbTokenizer(
				string);
		MedicalTrainer.toLowerCase(tokens);
		MedicalTrainer.removeStopWords(tokens, 
				stopwordlist);
		if (stem)
			MedicalTrainer.simpleStem(tokens);
		MedicalTrainer.removeStopWords(tokens, 
				stopwordlist);
		
		StringBuilder sb = new StringBuilder();
		for(String tok: tokens) {
			if (tok.contains("-")) {
				StringTokenizer st  = new StringTokenizer(tok, "-");
				while (st.hasMoreTokens()) {
					sb.append(st.nextToken());
					sb.append(" ");
				}
			}
			else {
				sb.append(tok);
				sb.append(" ");
			}
		}
		String newstring = sb.toString().trim();
		LucenePTBTokenizer lptb = 
			new LucenePTBTokenizer(new StringReader(newstring));
		NgramTokenizer ntokenizer = new NgramTokenizer(lptb, ngram);
		ntokenizer.tokenize();			
		Vector<String> filteredString = new Vector<String>();
		while(ntokenizer.hasNext())
			filteredString.add((String)ntokenizer.next());
		MedicalTrainer.addToCounter(cntr, filteredString);			

		return cntr;
	}
	
	public  Counter<String> returnFreqDistOnSetOfNgrams() throws NumberFormatException, IOException 
	 {
		Counter<String> result = new IntCounter<String>();
		for (int ngram : this.ngramsToGet) {
			result.addAll(this.returnFreqDist(ngram));
		}
		return result;
	}
	/**
	 * Returns the global FreqDist
	 * @param filename
	 * @param textcol
	 * @param agecol
	 * @param lowvalue
	 * @param upvalue
	 * @param ngramsToget
	 * @param tolowercase
	 * @param stopwordlist
	 * @param skipHeader
	 * @return
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public  Counter<String> returnFreqDist( int ngramsToget)	
		throws NumberFormatException, IOException {
		
		Counter<String> cntr = new IntCounter<String>();
		CSVReader csvreader = new CSVReader(new FileReader(this.filename));
		String[] nextLine =null;
		if (this.skipHeader)
			csvreader.readNext();
		while ((nextLine = csvreader.readNext()) != null) {
			float age = (float)-1.0 ;
			if (this.agecol >= 0) {
				age = Float.parseFloat(nextLine[this.agecol-1]);
				if (age < this.lowageval || age > this.upageval) 
					continue;
			}	
			List<String> tokens = MedicalTrainer.ptbTokenizer(
					nextLine[this.textcol -1]);
			MedicalTrainer.toLowerCase(tokens);
			MedicalTrainer.removeStopWords(tokens,	stopwordlist);
			
			if (this.stem)
				MedicalTrainer.simpleStem(tokens);
			
			MedicalTrainer.removeStopWords(tokens, 
					this.stopwordlist);
			
			StringBuilder sb = new StringBuilder();
			for(String tok: tokens) {
				if (tok.contains("-")) {
					StringTokenizer st  = new StringTokenizer(tok, "-");
					while (st.hasMoreTokens()) {
						sb.append(st.nextToken());
						sb.append(" ");
					}
				}
				else {
					sb.append(tok);
					sb.append(" ");
				}
			}
			String newstring = sb.toString().trim();
			LucenePTBTokenizer lptb = 
				new LucenePTBTokenizer(new StringReader(newstring));
			NgramTokenizer ntokenizer = new NgramTokenizer(lptb, ngramsToget);
			ntokenizer.tokenize();			
			Vector<String> input = new Vector<String>();
			while(ntokenizer.hasNext())
				input.add((String)ntokenizer.next());
			MedicalTrainer.addToCounter(cntr, input);			
		}
		return cntr;
	}
	
	public WekaInstances returnRelativeProbabilityWeight(HashMap<Integer, 
			Counter<String>> counters, boolean addunknownClass)
	throws NumberFormatException, IOException {
		
		int numattributes = 0;
		HashMap<Integer, Distribution<String>> distmap
			= new HashMap<Integer,Distribution<String>>();
		
		
		for (Counter<String> cnt : counters.values()) {
			numattributes = numattributes + cnt.size();
			
		}		
		for (int i = 0; i < counters.size(); i++) {
			distmap.put(i,  Distribution.laplaceSmoothedDistribution(
					counters.get(i), counters.get(i).keySet().size()));
		}
		if (this.identifierCol >= 0)
			numattributes++;
		if (this.agecol >= 0)
			numattributes++;
		if (this.labelcol >= 0)
			numattributes++;
		WekaInstances wekainstances = new WekaInstances(this.datasetname, 	new ArrayList<Attribute>(),
				numattributes);
		wekainstances.setSparseInstance(true);
		int index = 0;
		if (this.identifierCol >= 0) {
			ArrayList<String> ambiguity=null;
			wekainstances.insertAttributeAt(new Attribute(this.identifierName,ambiguity),index++);
						
		}
		if (this.agecol >= 0) {
			wekainstances.insertAttributeAt(new Attribute("age"),index++);
		}
		
		for (Counter<String> cntr : counters.values()) {
			for (String s :cntr.keySet())
				wekainstances.insertAttributeAt(new Attribute(s),index++);
		}
		if (this.labelcol >= 0) {
			wekainstances.insertAttributeAt(new Attribute("class",	this.classvalues),index++);
			wekainstances.setClassIndex(index -1);
		}
		
		CSVReader csvreader = new CSVReader(new FileReader(this.filename));
		String[] nextLine =null;
		if (this.skipHeader)
			csvreader.readNext();
		while ((nextLine = csvreader.readNext()) != null) {
			float age = (float)-1.0 ;
			if (this.agecol >= 0) {
				age = Float.parseFloat(nextLine[this.agecol-1]);
				if (age < this.lowageval || age > this.upageval) 
					continue;
			}
			wekainstances.initWorkingInstance();
			wekainstances.addWorkingInstance();
			if (this.identifierCol >= 0) 
				wekainstances.setValueOfWorkingInstance(this.identifierName,
						nextLine[identifierCol-1]);
			if (agecol >= 0)
				wekainstances.setValueOfWorkingInstance("age",age);
		
			for (Counter<String> cntr : counters.values()) {
				for (String s :cntr.keySet())
					wekainstances.setValueOfWorkingInstance(s, 0.0);
			}
			
			for (int i=0; i < this.ngramsToGet.size(); i++) {
				Distribution<String> gdist = distmap.get(i);
				Counter<String> local = MedicalTrainer.returnFreqDist(
						nextLine[textcol-1],this.ngramsToGet.get(i), 
						this.lowercase,this.stem,	this.stopwordlist);
				Distribution<String> ldist = Distribution.laplaceSmoothedDistribution(
						local, local.keySet().size());
				for (String term : local.keySet()) {
					double localprob = ldist.probabilityOf(term);
					double value = gdist.probabilityOf(term) * (gdist.logProbabilityOf(term)
						- ldist.logProbabilityOf(term)); 
					wekainstances.setValueOfWorkingInstance(term, value, 
							true);
				}
			}
			String lclass = null;
			if (this.labelcol >= 0 && nextLine.length >= this.labelcol
					&& nextLine[this.labelcol -1].length() > 0) {
				lclass = nextLine[this.labelcol -1];
			//	System.out.println(lclass);
				wekainstances.setValueOfWorkingInstance("class", lclass);
			}
			if (!addunknownClass || lclass == null ) {
				wekainstances.delete(wekainstances.numInstances() -1 );
			}
		}
		
		return wekainstances;
	}
	
	public static void printToFile(String filename, Counter<String> counter , int numDocs) {
		
		try {
			CSVWriter csvWriter= new CSVWriter(new FileWriter(new File(filename)));
			String[] row = new String[4];
			for (String key : counter.keySet()) {
				row[0] = key;
				double val = counter.getCount(key);
				row[1] = new String(""+val);
				row[2] = new String("" + (val/numDocs));
				csvWriter.writeNext(row);
				
			}
			csvWriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	
	public void evaluate() throws Exception {
		/*
		if (this.cltype.equals(ClassifierType.simplelogistic)) {
			Enumeration enm = ((SimpleLogistic)this.classifier).enumerateMeasures();
			while(enm.hasMoreElements())
				System.out.println(enm.nextElement());
		}
		*/
		Instances instances = CommonClassifierRoutines.removeAttributes(
				this.trainingSet,this.indicesToRemove);
		Evaluation eval = new Evaluation(instances);
		eval.crossValidateModel(this.classifier, instances, instances.numInstances(), 
				new Random(1000));
		System.out.println(eval.toSummaryString("\nResults\n======\n", false));
		
	}
	
	public List<String> getDocIds() throws IOException {
		ArrayList<String> docs = new ArrayList<String>();
		
		CSVReader csvreader = new CSVReader(new FileReader(this.filename));
		if (this.skipHeader)
			csvreader.readNext();
		String[] row=null;
		while((row = csvreader.readNext()) != null) {
			float age = Float.parseFloat(row[this.agecol -1]);
			if (age < this.lowageval || age > this.upageval)
				continue;
			if (row.length < this.labelcol || 
					row[this.labelcol -1].trim().length() <= 0)
				continue;
			if (!docs.contains(row[this.identifierCol-1]))
				docs.add(row[this.identifierCol-1]);
			else
				System.out.println("don't want you to be here method  : getDocIds");
		}
		return docs;
	}
	
	public List<MultinomialDocumentModel> populateModels() throws IOException {
		List<MultinomialDocumentModel> models = 
			new ArrayList<MultinomialDocumentModel>();
		
		for (int ngram : this.ngramsToGet) {
			MultinomialDocumentModel md = new MultinomialDocumentModel(
					new String(""+ngram));
			for (Record record : this.population) {			
				md.addDocModel(record.getId(), this.returnTokens(
						record.getText(), ngram),record.getTextcategory());
			}
			models.add(md);
		}
		return models;
	}
	
	
	public Instance testInstanceOnLanguageModel(List<MultinomialDocumentModel> containers,
			WekaInstances instances, String docId) throws IOException {
		
		for(Record record : this.population) {
			if (!docId.equals(record.getId()))
				continue;
			String text = record.getText();
			instances.initWorkingInstance();
			instances.setValueOfWorkingInstance("id", record.getId());
			instances.setValueOfWorkingInstance("age", record.getAge());
			
			for (MultinomialDocumentModel md : containers) {
				List<String> X = null;
				if (md.getModelName().equalsIgnoreCase("1"))
					X = this.returnTokens(text, 1);
				else if (md.getModelName().equalsIgnoreCase("2"))
					X = this.returnTokens(text, 2);
				else if (md.getModelName().equalsIgnoreCase("3"))
					X = this.returnTokens(text, 3);
				for (String topic : md.getTopics()) {
					String attname = topic+"_"+md.getModelName();
					instances.setValueOfWorkingInstance(attname,
							md.getProbOfClassGivenDocument(X, topic));
				}
			}
			instances.setValueOfWorkingInstance("class", record.getTextcategory());
			Instance toreturn = instances.getWorkingInstance();
			instances.initWorkingInstance();
			return toreturn;
		}
		return null;
	}
	
	public  List<Record> populateRecords() throws IOException{
		if (this.population == null)
			this.population = new ArrayList<Record>();
		CSVReader csvreader = new CSVReader(new FileReader(this.filename));
		if (this.skipHeader)
			csvreader.readNext();
		String[] row = null;
		while ((row = csvreader.readNext()) != null) {
			float age = Float.parseFloat(row[this.agecol -1]);
			if (age < this.lowageval || age > this.upageval)
				continue;
			if (row.length < this.labelcol || 
					row[this.labelcol -1].trim().length() <= 0)
				continue;
			Record record = new Record(row[this.identifierCol-1].trim());
			record.setAge(age);
			record.setText(row[this.textcol -1].trim());
			record.setTextcategory( row[this.labelcol-1].trim());
			this.population.add(record);
		}
		return this.population;
	}
	public  WekaInstances trainingOnLanguageModel(List<MultinomialDocumentModel> containers
			, String doctoexclude) throws IOException {
		
		WekaInstances instances = new WekaInstances(this.datasetname,
				new ArrayList<Attribute>(), 0);
		int index=0;
		instances.addStringAttribute("id", index++);
		instances.insertAttributeAt(new Attribute("age"), index++);
		for (MultinomialDocumentModel md : containers) {
			for (String topic : md.getTopics()) {
				instances.insertAttributeAt(new Attribute(topic+"_"
						+md.getModelName()), index++);
			}			
		}
		
		ArrayList<String> topics = new ArrayList<String>();
		for (MultinomialDocumentModel md : containers) {
			for (String topic : md.getTopics()) {
				if (!topics.contains(topic))
					topics.add(topic);
			}
		}
		
		instances.addClassAttribute("class",topics);
		
		for (Record record : this.population) {
			instances.initWorkingInstance();
			if (doctoexclude != null && record.getId().equals(doctoexclude))
				continue;
			
			instances.setValueOfWorkingInstance("id", record.getId());
			instances.setValueOfWorkingInstance("age", record.getAge());
			for (MultinomialDocumentModel md : containers) {
				List<String> X = null;
				if (md.getModelName().equalsIgnoreCase("1"))
					X = this.returnTokens(record.getText(), 1);
				else if (md.getModelName().equalsIgnoreCase("2"))
					X = this.returnTokens(record.getText(), 2);
				else if (md.getModelName().equalsIgnoreCase("3"))
					X = this.returnTokens(record.getText(), 3);
				for (String topic : md.getTopics()) {
					String attname = topic+"_"+md.getModelName();
					instances.setValueOfWorkingInstance(attname,
							md.getProbOfClassGivenDocument(X, topic));
				}
			}
			//System.out.println( row[this.labelcol -1]);
			instances.setValueOfWorkingInstance("class", record.getTextcategory());
			instances.addWorkingInstance();
		}
		
		return instances;
	}
	
	
	public void doLanguageModelClassification() throws Exception {
		List<MultinomialDocumentModel> container = this.populateModels();
		
		int missclassifier = 0;
		int count =0;
		for (Record record : this.population) {
			String docid = record.getId();
			for (MultinomialDocumentModel md : container) {
				md.storeAndRemoveDoc(docid);
			}
			
			WekaInstances training = this.trainingOnLanguageModel(container, docid);
			
		
			AbstractClassifier classifier =  
				CommonClassifierRoutines.trainOnInstances(this.classifier ,training, 
						new int[] {0,1}, this.cloptions);
			training.clear();
			Instance testinstance = this.testInstanceOnLanguageModel(container,
					training, docid);
			if (!CommonClassifierRoutines.testInstances(classifier, testinstance, 
					training,new int[] {0,1})) {
				missclassifier++;
				System.out.println("incrementing missclassififcaion "+missclassifier +
						" round number "+ count + " doc id" + docid + " age "+ record.getAge());
				
			}
			for (MultinomialDocumentModel md : container) {
				md.storeAndRemoveDoc(docid);
			}
			count++;
		}
		System.out.println(missclassifier+ " "+this.population.size());
		
	}
}