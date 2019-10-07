package dk.sdu.kpm.algo.fdr;

import dk.sdu.kpm.KPMSettings;
import dk.sdu.kpm.graph.GeneEdge;
import dk.sdu.kpm.graph.GeneNode;
import dk.sdu.kpm.graph.KPMGraph;
import dk.sdu.kpm.graph.Result;
import edu.uci.ics.jung.algorithms.filters.Filter;
import edu.uci.ics.jung.graph.SparseGraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.graph.util.Pair;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.Buffer;
import java.util.*;
import java.io.FileWriter;


import java.io.Serializable;

/**
 * Class that create subgraphs of a KPMGraph, but does not provide the full functionalities
 * of a KPM Graph
 * however povides other functionalities like p-values
 */

public class RandomSubgraph extends SparseGraph<GeneNode, GeneEdge> implements Serializable, Result {

    public double getPval() {
        return pval;
    }

    private double pval = 1.0;

    public double getGeneralPval() {
        return generalPval;
    }

    private double generalPval = 1.0;

    public double getTestStatistics() {
        return testStatistics;
    }

    private double testStatistics = -1;

    public double getGeneralTeststat() {
        return generalTeststat;
    }


 // Subscores for last N Nodes
    private double generalTeststatLastN = -1;

    public double getPvalLastN() {
        return pvalLastN;
    }

    private double pvalLastN = 1.0;

    public double getGeneralPvalLastN() {
        return generalPvalLastN;
    }

    private double generalPvalLastN = 1.0;

    public double getTestStatisticsLastN() {
        return testStatisticsLastN;
    }

    private double testStatisticsLastN = -1;

    public double getGeneralTeststatLastN() {
        return generalTeststatLastN;
    }



    private double generalTeststat = -1;
    private boolean is_significant = false;
    private double significanceLevel = 0.05;

    private KPMSettings kpmSettings;

    public String id;

    public void setMaxDistanceFromThresh(double maxDistanceFromThresh) {
        this.maxDistanceFromThresh = maxDistanceFromThresh;
    }

    public double getMaxDistanceFromThresh() {
        return maxDistanceFromThresh;
    }

    private double maxDistanceFromThresh = 0.0;

    // per node Score is the average Score of each Node in the network
    // it is used to compare the quality of differnt networks when using
    // an additive scoring system.
    public double getPerNodeScore() {
        return perNodeScore;
    }

    public void setPerNodeScore(double perNodeScore) {
        this.perNodeScore = perNodeScore;
    }

    private double perNodeScore = -1.0;


    public int getN() {
        return n;
    }

    public void setN(int n) {
        this.n = n;
    }

    //Stores the last n GeneNodes added in a run -> Sliding window approach
    private int n = 20;



    public List<GeneNode> getLastNNodes() {
        return lastNNodes;
    }

    private List<GeneNode> lastNNodes = new LinkedList<>();


    //score tracker
    private List<Double> scoreTracker = new ArrayList();

    public void setMinInd(int minInd) {
        this.minInd = minInd;
    }

    public int getMinInd(){
        return this.minInd;
    }

    private int minInd = 0;


    public void add2ScoreTracker(double score){
        this.scoreTracker.add(score);
    }
    public List<Double> getScoreTracker(){
        return this.scoreTracker;
    }
    public void setScoreTracker(List<Double> scoreTracker){
        this.scoreTracker = scoreTracker;
    }

    boolean writeBackground=false;

    /*
    * this constructor can be used to generate the random networks for the background distribution
     */
    public RandomSubgraph(KPMGraph kpmGraph, int size, boolean includeBackgroundNodes, String filename, KPMSettings kpmSettings) {
        super();
        this.kpmSettings = kpmSettings;
        this.id = System.currentTimeMillis()+"graph";
        this.n= kpmSettings.SLIDING_WINDOW_SIZE;
        generateRandomSizeN(kpmGraph, size, includeBackgroundNodes, filename);
    }
    /*
    * This constructor will be used by the Algorithm to generate candidate networks
     */

    public RandomSubgraph(GeneNode node, KPMSettings kpmSettings){
    //super();
        this.kpmSettings = kpmSettings;
        this.n= kpmSettings.SLIDING_WINDOW_SIZE;
        this.id = System.currentTimeMillis()+"graph";
    this.addVertex(node);
    }

    public RandomSubgraph(KPMSettings kpmSettings){
        this.kpmSettings = kpmSettings;
        this.n= kpmSettings.SLIDING_WINDOW_SIZE;
    }

    /*
    Adds a Genenode to lastNNodes making sure, that the capacity n is not exceeded.
     */
    public void add2lastNNodes(GeneNode node){
        if(lastNNodes.size()<n){
            lastNNodes.add(node);
        }
        if(lastNNodes.size()>=n){
            lastNNodes.remove(0);
            lastNNodes.add(node);
        }
    }



    private void generateRandomSizeN(KPMGraph kpmGraph, int size, boolean includeBackgroundNodes, String filename) {
        Random rand = kpmSettings.R;
        int randomNodeIndex;
        GeneNode[] nodes = kpmGraph.getVertices().toArray(new GeneNode[kpmGraph.getVertices().size()]);
        GeneNode nextNode = null;

        //Choose first node
        boolean first = false;
        while(!first) {
            randomNodeIndex = rand.nextInt(kpmGraph.getVertices().size());
            nextNode = nodes[randomNodeIndex];
            if (!kpmGraph.getBackNodesMap().containsKey(nextNode.nodeId) || includeBackgroundNodes) {
                first = this.addVertex(nextNode);
            }

        }
        ArrayList<GeneNode> candidates = new ArrayList<GeneNode>();
        candidates.addAll(kpmGraph.getNeighbors(nextNode));
        int i = 1;
        while (i < size) {
            if (candidates.size() == 0) {
                //System.out.println("Ended: no remaining candidate");
                break;
            }
            randomNodeIndex = rand.nextInt(candidates.size());
            nextNode = candidates.get(randomNodeIndex);
            //if node can be added, remove node from candidate list, but add all neighbours
            // do not sample nodes not contained in the expression data
            if ((!kpmGraph.getBackNodesMap().containsKey(nextNode.nodeId)|| includeBackgroundNodes) && this.addVertex(nextNode)) {

                candidates.remove(randomNodeIndex);
                candidates.addAll(kpmGraph.getNeighbors(nextNode));
                // TODO. random
                //System.out.println(remo.size());
                i++;
                // add all edges for the newly created node
                for (GeneEdge e : kpmGraph.getOutEdges(nextNode)) {
                    if (this.containsVertex(kpmGraph.getEndpoints(e).getFirst()) &&
                            this.containsVertex(kpmGraph.getEndpoints(e).getSecond())) {
                        addEdge(e, new Pair<GeneNode>(kpmGraph.getEndpoints(e)), EdgeType.UNDIRECTED);
                    }
                }
            }
            // if node can't be added (because it is already contained in node, remove it - this
            // will lead to all nodes being removed eventually, if the network size is smaller than the
            // targeted subnetwork.
            else {
                candidates.remove(randomNodeIndex);
            }
        }
        calculateNetworkScore(kpmSettings.AGGREGATION_METHOD, kpmGraph);
        if(writeBackground) {
            writeToFile(filename + "pvalsSamplewise.txt", filename + "nodeDist.txt", filename + "pvalsGeneral.txt");
        }
    }


    protected int calculateNetworkScore(String method, KPMGraph copy){
        int degFree = -1;
        switch(method){
            case "mean":
                degFree =  calculateMeanPval();
                break;
            case "fisher":
                degFree =  calculatePvalFisher();
                //significanceTest(degFree, this.testStatistics, this.significanceLevel);
                degFree = calculateGeneralPvalFisher();
                break;
            case "median":
                degFree = calculateMedianPval();
                break;
            case "sum":
                degFree = calculateSumPval();
                break;
            case "normSum":
                degFree = calculateNormalizedSumPval();
                break;
            case "normDegSum":
               degFree = calculateNodeDegreeNormSumPval(copy);
                break;
            case "meanLog":
                degFree = calculateMeanLogPval();
                break;
            default:
                System.exit(1);
        }
        return degFree;
    }

    private int calculateMedianPval(){
        double[] nodes = new double[this.getVertexCount()];
        double[] medianTest = new double[this.getVertexCount()];
        int i = 0;
        for(GeneNode n: this.getVertices()){
            nodes[i]=Math.abs(n.getAveragePvalue().get("L1"));
            medianTest[i] = Math.abs(n.getPvalue());
            i++;
        }
        Arrays.sort(nodes);
        Arrays.sort(medianTest);
        int medIndx = (int)Math.floor(nodes.length/2.0);
        this.testStatistics = nodes[medIndx];
        this.generalTeststat = medianTest[medIndx];
        return this.getVertexCount();
    }


    private int calculateNodeDegreeNormSumPval(KPMGraph copy){
        double sum = 0.0;
        double sumGeneral = 0.0;
        for(GeneNode n : this.getVertices()){
            double nrNode = copy.getNeighborCount(n)*1.0;
            sum+=n.getAveragePvalue().get("L1")*nrNode;
            sumGeneral+= n.getPvalue()*nrNode;

        }
        this.generalTeststat = sumGeneral;
        this.testStatistics = sum;

        sum = 0.0;
        sumGeneral = 0.0;
        for(GeneNode n : this.lastNNodes){
            double nrNode = copy.getNeighborCount(n)*1.0;
            //double nrNode = n.getAverageNeighborExpression();
            //double nrNode = 1.0;
            sum+=n.getAveragePvalue().get("L1")*nrNode;
            sumGeneral+= n.getPvalue()*nrNode;

        }
        this.generalTeststatLastN = sumGeneral;
        this.testStatisticsLastN = sum;
        return this.getVertexCount();
    }

    private int calculateNormalizedSumPval(){
        double sum = 0.0;
        double sumGeneral = 0.0;
        for(GeneNode n : this.getVertices()){
            int nrNode = this.getNeighborCount(n);
            sum+=n.getAveragePvalue().get("L1");
            sumGeneral+= n.getPvalue();
        }
        this.generalTeststat = sumGeneral/this.getVertexCount();
        this.testStatistics = sum/this.getVertexCount();
        /*double sum = 1.0;
        double sumGeneral = 1.0;
        for(GeneNode n : this.getVertices()){
            sum+=Math.log(n.getAveragePvalue().get("L1"));
            sumGeneral+= Math.log(n.getPvalue());
        }
        this.generalTeststat = Math.abs(sumGeneral);
        this.testStatistics = Math.abs(sum);*/

        return this.getVertexCount();
    }

    private int calculateSumPval(){
        double sum = 0.0;
        double sumGeneral = 0.0;
        for(GeneNode n : this.getVertices()){
            sum+=n.getAveragePvalue().get("L1");
            sumGeneral+= n.getPvalue();
        }
        this.generalTeststat = sumGeneral;
        this.testStatistics = sum;

        sum = 0.0;
        sumGeneral = 0.0;
        for(GeneNode n : this.lastNNodes){
            sum+=n.getAveragePvalue().get("L1");
            sumGeneral+= n.getPvalue();
        }
        this.generalTeststatLastN = sumGeneral;
        this.testStatisticsLastN = sum;

        return this.getVertexCount();
    }

    private int calculateMeanLogPval(){
        double sum = 0.0;
        double sumGeneral = 0.0;
        for(GeneNode n : this.getVertices()){
            sum+=Math.log10(n.getAveragePvalue().get("L1"))*-1;
            sumGeneral+= Math.log10(n.getPvalue())*-1;
        }
        this.generalTeststat = sumGeneral/this.getVertexCount();
        this.testStatistics = sum/this.getVertexCount();

        sum = 0.0;
        sumGeneral = 0.0;
        for(GeneNode n : this.lastNNodes){
            sum+=Math.log10(n.getAveragePvalue().get("L1"))*-1;
            sumGeneral+= Math.log10(n.getPvalue())*-1;
        }
        this.generalTeststatLastN = sumGeneral/this.getVertexCount();
        this.testStatisticsLastN = sum/this.getVertexCount();

        return this.getVertexCount();
    }


    private int calculateMeanPval(){

        double sumOfPvals = 0.0;
        double sumOfGeneralPvals = 0.0;

        for (GeneNode n: this.getVertices()){
            // Use absolute values here to account for possibly negative zscores.
            sumOfPvals=sumOfPvals+Math.abs(n.getAveragePvalue().get("L1"));
            sumOfGeneralPvals = sumOfGeneralPvals + Math.abs(n.getPvalue());
        }
        double meanPval = sumOfPvals/this.getVertexCount();
        double meanGenPval = sumOfGeneralPvals/this.getVertexCount();

        this.testStatistics = meanPval;
        this.generalTeststat = meanGenPval;
        return this.getVertexCount();
    }



    private int calculatePvalFisher() {
        double sumOfLogPvals = 0;
        int infcounter = 0;
        for (GeneNode n : this.getVertices()) {
            // TODO: Currently random value is chosen
            String akey = n.averagePvalue.keySet().toArray(new String[n.averagePvalue.keySet().size()])[0];
            if(!akey.equals("L1")){
                System.out.println("not L1");
            }
            sumOfLogPvals += Math.log(n.averagePvalue.get(akey));

            if(Double.isInfinite(Math.log(n.averagePvalue.get(akey)))){
                System.out.println(n.nodeId);
                infcounter ++;
            }

        }
        //System.out.println(sumOfLogPvals);
        double testStat = -2 * sumOfLogPvals;
        this.testStatistics = testStat;
        int deg = 2*this.getVertices().size();
        significanceTest(deg, this.testStatistics, 0.05);
        //System.out.println(infcounter);
        return this.getVertices().size();
    }

    private void significanceTest(int degFreedom, double testStatistics, double significanceLevel) {
        ChiSquaredDistribution chiSquare = new ChiSquaredDistribution(degFreedom,  1.0E-20 );
        if(!Double.isInfinite(testStatistics)) {
            this.pval = 1.0- chiSquare.cumulativeProbability(testStatistics);
        }
        else{
            this.pval = 0.0;
        }
        if (this.pval <= significanceLevel) {
            this.is_significant = true;
        }
    }

    // TODO remove ugly code duplicates
    protected int calculateGeneralPvalFisher() {
        double sumOfLogPvals = 0;
        int infcounter = 0;
        for (GeneNode n : this.getVertices()) {

            sumOfLogPvals += Math.log(n.getPvalue());

            if(Double.isInfinite(Math.log(n.getPvalue()))){
                System.out.println(n.nodeId);
                infcounter ++;
            }

        }
        //System.out.println(sumOfLogPvals);
        double testStat = -2 * sumOfLogPvals;
        this.generalTeststat = testStat;
        int deg = 2*this.getVertices().size();
        generalSignificanceTest(deg, this.generalTeststat, 0.05);
        //System.out.println(infcounter);
        return this.getVertices().size();
    }

    private void generalSignificanceTest(int degFreedom, double testStatistics, double significanceLevel) {
        ChiSquaredDistribution chiSquare = new ChiSquaredDistribution(degFreedom,  1.0E-220 );
        if(!Double.isInfinite(testStatistics)) {
            this.generalPval = 1.0- chiSquare.cumulativeProbability(testStatistics);
        }
        else{
            this.generalPval = 0.0;
        }
        if (this.generalPval <= significanceLevel) {
            this.is_significant = true;
        }
    }

    public void copyTeststats(RandomSubgraph rs){
        this.testStatistics = rs.testStatistics;
        this.generalTeststat = rs.generalTeststat;
        this.pval = rs.pval;
        this.generalPval = rs.generalPval;
        this.kpmSettings = rs.kpmSettings;
    }

    public void writeToFile(String filename, String filename2, String pvalsAll) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename,true));
             BufferedWriter pvall = new BufferedWriter(new FileWriter(pvalsAll,true));
             BufferedWriter degreeWriter = new BufferedWriter(new FileWriter(filename2,true))) {
            int counter = this.getVertices().size();
            // first column: network size
            degreeWriter.write(counter+"\t");
            bw.write(counter+"\t"+ this.pval+"\t"+this.testStatistics+"\t");
            pvall.write(counter+"\t"+this.generalPval+"\t"+this.generalTeststat +"\t");
            for (GeneNode n : this.getVertices()) {
                    if(counter>1) {
                        degreeWriter.write(this.getNeighbors(n).size() + "\t");
                        bw.write(n.getAveragePvalue().get(n.getAveragePvalue().keySet().toArray()[0]) + "\t");
                        pvall.write(n.getPvalue()+"\t");
                    }
                    else
                    {
                        degreeWriter.write(this.getNeighbors(n).size()+"");
                        bw.write(n.getAveragePvalue().get(n.getAveragePvalue().keySet().toArray()[0])+"");
                        pvall.write(n.getPvalue()+"");

                    }
                    counter--;

                }
                degreeWriter.write("\n");
                bw.write("\n");
                pvall.write("\n");
        }
        catch (IOException e){
            e.printStackTrace(

            );
        }
    }

    public void writeGraphToFile(String filename, String name, boolean general){
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename+".graph", true));
                 BufferedWriter stat = new BufferedWriter(new FileWriter(filename+".stat", true));
                 BufferedWriter nodes = new BufferedWriter(new FileWriter(filename+".nodes", true));
                 BufferedWriter evol = new BufferedWriter(new FileWriter(filename+".tracker", true))){
                // file for test p value and teststatistics for each solution
                if(!general) {
                    stat.write(name + "\t" + this.pval + "\t" + this.testStatistics +"\t"+this.getMaxDistanceFromThresh()+"\n");
                    // separate entries for each node
                    for (GeneNode n : this.getVertices()) {
                        nodes.write(n.nodeId+"\t"+name +"\t"+n.getAveragePvalue().get("L1") +"\t"+ this.getNeighborCount(n) + "\n");
                    }
                }
                else{
                    stat.write(name + "\t" + this.getGeneralPval() + "\t" + this.generalTeststat +"\t"+this.getMaxDistanceFromThresh()+ "\n");
                    // separate entries for each node
                    for (GeneNode n : this.getVertices()) {
                        nodes.write(n.nodeId+"\t"+name +"\t"+n.getPvalue()  +"\t"+ this.getNeighborCount(n) + "\n");
                    }
                }

                //write graph to file in sif format
                for (GeneEdge e : this.getEdges()) {
                    bw.write(name +"\t"+getEndpoints(e).getFirst() + "\tpp\t" + getEndpoints(e).getSecond() + "\n");
                }

                // write score evolution file.
                for(int i = 0; i<scoreTracker.size(); i++){
                    evol.write(name +"\t"+ (i+1)+ "\t"+ scoreTracker.get(i) + "\n");
                }

                //bw.append("###");
            } catch (IOException e) {
                e.printStackTrace();
            }
    }


    public boolean duplicated(RandomSubgraph sg){
        // Networks have same nodes or one is contained in the other
        boolean dup = (sg.getVertices().containsAll(this.getVertices())&&this.getVertices().containsAll(sg.getVertices()));

         return dup;
    }

    @Override
    public Map<String, GeneNode> getVisitedNodes() {
        return null;
    }

    @Override
    public double getAverageDiffExpressedCases() {
        return 0;
    }

    @Override
    public double getInformationGainExpressed() {
        return 0;
    }

    @Override
    public int getFitness() {
        return 0;
    }

    @Override
    public int getInstances() {
        return 0;
    }

    @Override
    public void setInstances(int instances) {

    }

    @Override
    public int getNumExceptionNodes() {
        return 0;
    }

    @Override
    public int getNonDifferentiallyExpressedCases() {
        return 0;
    }

    @Override
    public void flagExceptionNodes() {

    }

    @Override
    public boolean haveOverlap(Object o) {
        return false;
    }

    @Override
    public int compareTo(Result o) {
        return 0;
    }
}
