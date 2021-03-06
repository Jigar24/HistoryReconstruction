import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.types.IDSorter;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelSequence;

import java.util.*;


/**
 * Class for Topic Modelling
 */
public class TopicModel {

    private List<String> topicTitles;


    /**
     * Computes clusters
     * @param documentList documents to cluster
     * @param r retriever object
     * @param numTopics no of clusters to create
     * @return
     * @throws Exception
     */
    public List<Cluster> getClusters(List<DocumentClass> documentList, Retriever r, int numTopics) throws Exception {

        if(documentList.isEmpty()) {
            System.out.println("Docs List is empty!");
            return new ArrayList<Cluster>();
        }

        MalletDataImporter importer = new MalletDataImporter(MalletDataImporter.PipeType.Array);

//        InstanceList instances = importer.readDirectory(new File(dirName));
        InstanceList instances = importer.readDocuments(documentList);

        // Create a model with 100 topics, alpha_t = 0.01, beta_w = 0.01
        //  Note that the first parameter is passed as the sum over topics, while
        //  the second is

        double alpha_t = 0.01;
        double alpha_sum = alpha_t * numTopics;
        ParallelTopicModel model = new ParallelTopicModel(numTopics, alpha_sum, 0.01);

        model.addInstances(instances);

        // Use two parallel samplers, which each look at one half the corpus and combine
        //  statistics after every iteration.
        model.setNumThreads(4);

        // FIXME : Run the model for 50 iterations and stop (this is for testing only,
        //  for real applications, use 1000 to 2000 iterations)
        model.setNumIterations(Globals.TOPIC_MODELLING_ITERATIONS);
        model.estimate();

//        File file = new File("topics.txt");
//        model.printDocumentTopics(file);

        HashMap<String, DocumentClass> docMap = new HashMap<String, DocumentClass>();
        for(DocumentClass doc : documentList) {
            DocumentClass d = new DocumentClass(-1,doc.getFilename(),doc.getTitle() + " " + doc.getContent());
            docMap.put(doc.getFilename(), d);
        }

        numTopics = model.getNumTopics();
        int[] topicCounts = new int[ numTopics ];

        List<Cluster> clusters = new ArrayList<Cluster>(numTopics);
        for(int i = 0; i < numTopics; ++i) {
            Cluster c = new Cluster(i, null);
            clusters.add(i, c);
        }

        for(int doc = 0; doc < model.data.size(); ++doc) {
            LabelSequence topicSequence = model.data.get(doc).topicSequence;
            int[] currentDocTopics = topicSequence.getFeatures();

            int docLen = currentDocTopics.length;

            // Count up the tokens
            for (int currentDocTopic : currentDocTopics) {
                topicCounts[currentDocTopic]++;
            }

            int mxTopic = -1;
            double mxWeight = -1.0;

            // And normalize
            for (int topic = 0; topic < numTopics; topic++) {
                double tmp = (model.alpha[topic] + topicCounts[topic]) / (docLen + model.alphaSum);
                if(tmp > mxWeight) {
                    mxTopic = topic;
                    mxWeight = tmp;
                }
            }

            DocumentClass dd = docMap.get(model.data.get(doc).instance.getName().toString());
            dd.setClusterID(mxTopic);
            clusters.get(mxTopic).addDocument(dd);

            Arrays.fill(topicCounts, 0);
        }

        topicTitles = getTopics(model, Globals.TOPIC_TITLE_WORD_COUNT);

        for(int i = 0; i < clusters.size(); ++i) {
            clusters.get(i).setTitle(topicTitles.get(i));
            if(Globals.DOC_SELECTION_METHOD == 1) {
                clusters.get(i).keepOnlyImpDocs();
            } else if(Globals.DOC_SELECTION_METHOD == 2) {
                if(clusters.get(i).getTitle().trim().isEmpty())
                    continue;
                List<String> arrList = new ArrayList<String>();
                for(DocumentClass d : clusters.get(i).getDocs())
                    arrList.add(d.getFilename());

                // FIXME : don't just search the input query, search using queryExpansion
                List<DocumentClass> docs = r.searchInGivenFiles(SearchQuery.getMainQuery(), arrList.toArray(new String[arrList.size()]), Globals.RETRIEVAL_RESULT_COUNT);

                // for debug purpose
                DebugLogger.addDocsInCluster(docs);

                docs = docs.subList(0, Math.min(Globals.CENTROID_DOCS_IN_CLUSTER, docs.size()));
                List<String> fnames = new ArrayList<String>(docs.size());
                for(DocumentClass x : docs)
                    fnames.add(x.getFilename());
                clusters.get(i).keepOnlyGivenDocs(fnames);
            }
        }

        return clusters;
    }


    /**
     * Computes cluster titles
     * @param model model to get titles from
     * @param numWords no of words in each title
     * @return
     */
    private List<String> getTopics(ParallelTopicModel model, int numWords) {

        List<String> topicList = new ArrayList<String>();

        ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();

        // Print results for each topic
        for (int topic = 0; topic < model.getNumTopics(); topic++) {
            StringBuilder out = new StringBuilder();
            TreeSet<IDSorter> sortedWords = topicSortedWords.get(topic);
            int word = 1;
            Iterator<IDSorter> iterator = sortedWords.iterator();

            while (iterator.hasNext() && word < numWords) {
                IDSorter info = iterator.next();
                out.append(model.alphabet.lookupObject(info.getID())).append(" ");
                word++;
            }
            topicList.add(topic, out.toString());
        }

        return topicList;
    }


    /**
     * @return cluster titles
     */
    public List<String> getTopicTitles() {
        return topicTitles;
    }

}