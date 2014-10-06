/*
* Onto2Graph.java 2014 amitjain
*/
package com.semantomatic.ontograph;

import java.io.File;

import org.apache.log4j.Logger;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.rest.graphdb.RestGraphDatabase;
import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

/**
 * @author amitjain
 *
 */
public class Onto2Graph {

    private GraphDatabaseService db;
    private static Logger logger = Logger.getLogger(Onto2Graph.class.getName());


    public Onto2Graph() {
        getGraphDb();
    }
    
    private void importOntology(OWLOntology ontology) throws Exception {
    OWLReasoner reasoner = new Reasoner(ontology);
        
        //Check if the ontology is consistent
        if (!reasoner.isConsistent()) {
            logger.error("Ontology is inconsistent");
            //throw your exception of choice here
            throw new Exception("Ontology is inconsistent");
        }
        logger.info("Ontology is consistent");
        Transaction tx = db.beginTx();
        try {
            Node thingNode = getOrCreateNodeWithUniqueFactory("owl:Thing");
            
            //For each class in signature
            for (OWLClass c :ontology.getClassesInSignature(true)) {
                String classString = c.toString();
                logger.info(classString);
                if (classString.contains("#")) {
                    classString = classString.substring(classString.indexOf("#")+1,classString.lastIndexOf(">"));
                }
                Node classNode = getOrCreateNodeWithUniqueFactory(classString);

                NodeSet<OWLClass> superclasses = reasoner.getSuperClasses(c, true);

                //Get the superclasses
                if (superclasses.isEmpty()) {
                    classNode.createRelationshipTo(thingNode,DynamicRelationshipType.withName("isA"));    
                } else {
                    for (org.semanticweb.owlapi.reasoner.Node<OWLClass>parentOWLNode: superclasses) {
                        
                        OWLClassExpression parent = parentOWLNode.getRepresentativeElement();
                        String parentString = parent.toString();
                        logger.info(parentString);
                        if (parentString.contains("#")) {
                            parentString = parentString.substring(parentString.indexOf("#")+1,parentString.lastIndexOf(">"));
                        }
                        Node parentNode = getOrCreateNodeWithUniqueFactory(parentString);
                        classNode.createRelationshipTo(parentNode,DynamicRelationshipType.withName("isA"));
                    }
                }


                //For each instance of a class
                for (org.semanticweb.owlapi.reasoner.Node<OWLNamedIndividual> in : reasoner.getInstances(c, true)) {
                    OWLNamedIndividual i = in.getRepresentativeElement();
                    String indString = i.toString();
                    logger.info(indString);
                    if (indString.contains("#")) {
                        indString = indString.substring(indString.indexOf("#")+1,indString.lastIndexOf(">"));
                    }
                    Node individualNode = getOrCreateNodeWithUniqueFactory(indString);
                                             
                    individualNode.createRelationshipTo(classNode,DynamicRelationshipType.withName("isA"));

                    //For each property used on the instance
                    for (OWLObjectPropertyExpression objectProperty:
                     ontology.getObjectPropertiesInSignature()) {

                       for  
                       (org.semanticweb.owlapi.reasoner.Node<OWLNamedIndividual> object: reasoner.getObjectPropertyValues(i,objectProperty)) {
                            String reltype = objectProperty.toString();
                            logger.info(reltype);
                            reltype = reltype.substring(reltype.indexOf("#")+1, reltype.lastIndexOf(">"));
                            
                            String s = object.getRepresentativeElement().toString();
                            s = s.substring(s.indexOf("#")+1,s.lastIndexOf(">"));
                            Node objectNode = getOrCreateNodeWithUniqueFactory(s);
                            individualNode.createRelationshipTo(objectNode, DynamicRelationshipType.withName(reltype));
                        }
                    }

                    for (OWLDataPropertyExpression dataProperty:
                     ontology.getDataPropertiesInSignature()) {

                        for (OWLLiteral object: reasoner.getDataPropertyValues(i, dataProperty.asOWLDataProperty())) {
                            String reltype =dataProperty.asOWLDataProperty().toString();
                            reltype = reltype.substring(reltype.indexOf("#")+1, reltype.lastIndexOf(">"));
                            
                            String s = object.toString();
                            logger.info(s);
                            individualNode.setProperty(reltype, s);
                        }
                    }
                }
            }
            logger.info("Processing Properties");
            //Process each property and get the domains and ranges in onto
            for (OWLObjectPropertyExpression objectProperty:ontology.getObjectPropertiesInSignature()) {
                for  (org.semanticweb.owlapi.reasoner.Node<OWLClass> dmn: reasoner.getObjectPropertyDomains(objectProperty, true)) {
                    
                    OWLClassExpression dmnOwl = dmn.getRepresentativeElement();
                    String dmnString = dmnOwl.toString();
                    logger.info(dmnString);
                    if (dmnString.contains("#")) {
                        dmnString = dmnString.substring(dmnString.indexOf("#")+1,dmnString.lastIndexOf(">"));
                    }
                    Node dmnClsNode = getOrCreateNodeWithUniqueFactory(dmnString);
                    
                    String reltype = objectProperty.toString();
                    logger.info(reltype);
                    reltype = reltype.substring(reltype.indexOf("#")+1, reltype.lastIndexOf(">"));
                       
                    for  (org.semanticweb.owlapi.reasoner.Node<OWLClass> rng: reasoner.getObjectPropertyRanges(objectProperty, true)) {
                        
                        OWLClassExpression rngOwl = rng.getRepresentativeElement();
                        String rngString = rngOwl.toString();
                        logger.info(rngString);
                        if (rngString.contains("#")) {
                            rngString = rngString.substring(rngString.indexOf("#")+1,rngString.lastIndexOf(">"));
                        }
                        Node rngClsNode = getOrCreateNodeWithUniqueFactory(rngString);   
                        dmnClsNode.createRelationshipTo(rngClsNode, DynamicRelationshipType.withName(reltype));
                    
                    }
                }
            }
            
            
        } finally {
            tx.success();
        }
    }


    private Node getOrCreateNodeWithUniqueFactory(String s) throws Exception {
        Node dataNode = null;
        Label label = DynamicLabel.label( "Ontology" );
        try {
            Index<Node> nodeIndex = getGraphDb().index().forNodes("OntClasses");
            dataNode = nodeIndex.get("name",s).getSingle();
            if(dataNode == null) {
                dataNode = db.createNode(label);
                dataNode.setProperty("name", s);   
                nodeIndex.add(dataNode, "name", s);
            }
            logger.info("new nodeId = " + dataNode.getId());

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw e;
        } 
        return dataNode;
    }
    

    
    public void loadOntoIntoNeo() throws Exception {
        File fileBase = new File("src/test/resources/files/musimilarity.owl");

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ont = manager.loadOntologyFromOntologyDocument(fileBase);
        importOntology(ont);
        logger.info("Loading ontology into Neo Done");
    }
    
    public GraphDatabaseService getGraphDb() {

        if (db == null) {
            db= new RestGraphDatabase("http://localhost:7476/db/data");
            registerShutdownHook();
        }

        return db;
    }
    
    
    
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                db.shutdown();
            }
        });
    }

}