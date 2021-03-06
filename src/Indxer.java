import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Class for indexing files,
 * files are indexed to have better control over search
 */
public class Indxer {

    private IndexWriter indxWriter = null;
    public static int dd = 0;
    private long totalNoOfWords = 0;

    /**
     * Constructs indexWriter and sets where to store the indexed data
     * @param indxDir shows where to store indexed data
     * @throws IOException
     */
    public Indxer(String indxDir) throws IOException {
        // reset word count
        totalNoOfWords = 0;

        Directory dir = FSDirectory.open(new File(indxDir));
        Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_46, CharArraySet.EMPTY_SET);
        IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_46, analyzer);

        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE); // remove any previous indxes
//        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);   // keep prev indxes

        indxWriter = new IndexWriter(dir, iwc);
    }


    /**
     * Indexes the directory given as argument
     * @param dataDir directory to index
     * @throws IOException
     * @throws XMLStreamException
     */
    public void indxDir(String dataDir) throws IOException, XMLStreamException {
        System.out.println("Indexing Dir : " + dataDir);
        final File docDir = new File(dataDir);
        if (!docDir.canRead()) {
            System.out.println("Can't read data Directory");
            return;
        }

        if (!docDir.isDirectory()) {
            System.out.println("[ "+dataDir+" ] is not a Directory");
            return;
        }

        File[] files = docDir.listFiles();
        if(files == null || files.length == 0) {
            System.out.println("[ "+dataDir+" ] is empty!");
            return;
        }

        for (File f : files) {
            if(f.isDirectory()) {
                indxDir(f.getAbsolutePath());
            } else {
                indxFile(f);
            }
        }
    }


    /**
     * Indexes individual file
     * @param file file to index
     * @throws IOException
     * @throws XMLStreamException
     */
    public void indxFile(File file) throws IOException, XMLStreamException {
        FileInputStream fis = new FileInputStream( file );
        org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
        XmlDocument xmldoc = new XmlDocument(file.getAbsolutePath());
        if(xmldoc.isError()) {
            fis.close();
            return;
        }
        if(xmldoc.getTitle() == null) {
            xmldoc.setTitle("DEFAULT");
        }
        String[] dates = IRUtils.extractDate(xmldoc.getContent(), xmldoc.getFilename());
        if(dates == null) {
            dd++;
            return;
        }
        String delim = "";
        StringBuilder sb = new StringBuilder();
        for (String s : dates) {
            sb.append(delim).append(s);
            delim = " ";
        }
        String dateData = sb.toString();
        if(dateData.isEmpty()) {
            dd++;
            return;
        }
        doc.add(new StringField("filename", xmldoc.getFilename(), Field.Store.YES));
        doc.add(new TextField("title", xmldoc.getTitle(), Field.Store.YES));
        doc.add(new TextField("date", dateData, Field.Store.YES));
        doc.add(new TextField("contents", xmldoc.getContent(), Field.Store.YES));

        totalNoOfWords += xmldoc.getWordCount();

        if (indxWriter.getConfig().getOpenMode() == IndexWriterConfig.OpenMode.CREATE) {
            indxWriter.addDocument(doc);
        } else {
            indxWriter.updateDocument(new Term("title", xmldoc.getFilename()), doc);
        }
        fis.close();
    }

    /**
     * After writing indexes it is necessary to kill the writer,
     * this closes the file stream
     * @throws IOException
     */
    public void killWriter() throws IOException {
        Settings.setTotalNoOfWords(totalNoOfWords);
        LuceneUtils.TotalWordCount = totalNoOfWords;
        if(indxWriter != null) indxWriter.close();
    }
}
