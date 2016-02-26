package org.nuxeo.studioviz.helper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.connect.data.DownloadablePackage;
import org.nuxeo.connect.packages.PackageManager;
import org.nuxeo.ecm.platform.commandline.executor.api.CmdParameters;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandNotAvailable;
import org.nuxeo.ecm.platform.commandline.executor.service.CommandLineExecutorComponent;
import org.nuxeo.jaxb.Component;
import org.nuxeo.jaxb.Component.Extension;
import org.nuxeo.jaxb.Component.Extension.Action;
import org.nuxeo.jaxb.Component.Extension.Action.Filter;
import org.nuxeo.jaxb.Component.Extension.Action.Filter.Rule;
import org.nuxeo.jaxb.Component.Extension.Action.Filter.Rule.Type;
import org.nuxeo.jaxb.Component.Extension.Chain;
import org.nuxeo.jaxb.Component.Extension.Doctype;
import org.nuxeo.jaxb.Component.Extension.Handler;
import org.nuxeo.jaxb.Component.Extension.Schema;
import org.nuxeo.jaxb.Component.Extension.TemplateResource;
import org.nuxeo.jaxb.Component.Extension.Type.ContentViews;
import org.nuxeo.jaxb.Component.Extension.Type.ContentViews.ContentView;
import org.nuxeo.jaxb.Component.Extension.Type.Layouts;
import org.nuxeo.jaxb.Component.Extension.Type.Layouts.Layout;
import org.nuxeo.runtime.api.Framework;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class GraphHelper {

    private Log logger = LogFactory.getLog(GraphHelper.class);
    public static final String SNAPSHOT_SUFFIX = "0.0.0-SNAPSHOT";
    public static final String EXTENSIONPOINT_CHAIN = "chains";
    public static final String EXTENSIONPOINT_EVENT_HANDLERS = "event-handlers";
    public static final String EXTENSIONPOINT_ACTIONS = "actions";
    public static final String EXTENSIONPOINT_SCHEMAS = "schema";
    public static final String EXTENSIONPOINT_DOCTYPE = "doctype";
    public static final String EXTENSIONPOINT_TYPES = "types";
    public static final String EXTENSIONPOINT_ROUTE_MODEL_IMPORTER = "routeModelImporter";
    public static final String COMMON_SCHEMAS = "common,dublincore,uid,task,file,picture,image_metadata,iptc,publishing,webcontainer,files";
    public static final String CONNECT_URL = "https://connect.nuxeo.com/nuxeo/site/studio/ide?project=";
    
    private ArrayList<String> automationList = new ArrayList<String>();
	
    public static boolean isSnapshot(DownloadablePackage pkg) {
		return ((pkg.getVersion() != null) && (pkg.getVersion().toString().endsWith("0.0.0-SNAPSHOT")));
	}

	public static DownloadablePackage getSnapshot(List<DownloadablePackage> pkgs) {
		for (DownloadablePackage pkg : pkgs) {
			if (isSnapshot(pkg)) {
				return pkg;
			}
		}
		return null;
	}
	
	public ArrayList<String> getAutomationList(){
		Collections.sort(automationList);
		return automationList;
	}

	public void writeToFile(String path, String content) {
		FileOutputStream fop = null;
		File file;
		try {
			file = new File(path);
			fop = new FileOutputStream(file);

			// if file doesnt exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}
			// get the content in bytes
			byte[] contentInBytes = content.getBytes();

			fop.write(contentInBytes);
			fop.flush();
			fop.close();

		} catch (IOException e) {
			logger.error("Error while writing into file ", e);
		} finally {
			try {
				if (fop != null) {
					fop.close();
				}
			} catch (IOException e) {
				logger.error("Error while writing into file ", e);
			}
		}
	}

	public static String cleanUpForDot(String content){
		 content = content.replaceAll("\\.", "");
		 content = content.replaceAll("\\/", "");
		 content = content.replaceAll("\\-", "_");
		 //content = content.replaceAll(".", "");
		 return content;
	}

	public String getStudioJar(){
		String studioJar = "";
		PackageManager pm = Framework.getLocalService(PackageManager.class);
	    List<DownloadablePackage> pkgs = pm.listRemoteAssociatedStudioPackages();
	    DownloadablePackage snapshotPkg = getSnapshot(pkgs);
	    String studioPackage = "";
	    if (snapshotPkg != null) {
	    	studioPackage = snapshotPkg.getId();
	    	studioJar = studioPackage.replace("-0.0.0-SNAPSHOT", "")+".jar";
	    } else {
	    	logger.info("No Studio Package found.");
	    }
	    return studioJar;
	}

	public void copyStudioJar(String url, String studioJar, String nuxeoHomePath, CommandLineExecutorComponent commandLineExecutorComponent) throws CommandNotAvailable, IOException{
    	//Create the studioviz folder if it doesn't exist
    	File dir = new File(nuxeoHomePath+File.separator+"studioviz");
    	if(!dir.exists()) {
    		try{
    			dir.mkdir();
    	    }
    	    catch(SecurityException se){
    	       logger.error("Error while creating the directory [studioviz]", se);
    	    }
    	}

        CmdParameters params2 = new CmdParameters();
        params2.addNamedParameter("studioJar", url);
        params2.addNamedParameter("dest", nuxeoHomePath+File.separator+"studioviz"+File.separator+studioJar);
        commandLineExecutorComponent.execCommand("copy-studio-jar", params2);
	}

	public void extractXMLFromStudioJar(String studioJar, String studiovizFolderPath) throws CommandNotAvailable, IOException{
		Runtime rt = Runtime.getRuntime();
	    String[] cmd = { "/bin/sh", "-c", "cd "+studiovizFolderPath+"; jar xf "+studioJar };    
	    Process p = rt.exec(cmd);
	    try {
			p.waitFor();
		} catch (InterruptedException e) {
			logger.error("Error while waiting for the studio jar extraction", e);
		}
	}

	public String generateModelGraphFromXML(String studioProjectName, String destinationPath, String studiovizFolderPath, CommandLineExecutorComponent commandLineExecutorComponent, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException{
		JAXBContext jc = JAXBContext.newInstance("org.nuxeo.jaxb");
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		String result = "";
		String map = "";
		Component component = (Component) unmarshaller.unmarshal(new File(studiovizFolderPath+File.separator+"OSGI-INF"+File.separator+"extensions.xml"));

		String schemas = "subgraph cluster_0 {\n"+
	 		 	   "	style=\"dashed\";\n"+
	 		 	   " 	label = \"Schemas\";\n";

		String docTypes = "subgraph cluster_1 {\n"+
	 		 	   "	style=\"dashed\";\n"+
	 		 	   " 	label = \"Document Types\";\n";

		String facets = "subgraph cluster_2 {\n"+
	 		 	   "	style=\"dashed\";\n"+
	 		 	   " 	label = \"Facets\";\n";

		result = "digraph M {\n"+
		    "graph [fontname = \"helvetica\", fontsize=11];\n"+
		    "node [fontname = \"helvetica\", fontsize=11];\n"+
		    "edge [fontname = \"helvetica\", fontsize=11];\n";
		List<Extension> extensions = component.getExtension();

		int nbSchemas = 0;
		int nbDocTypes = 0;
		int nbFacets = 0;
		ArrayList<String> docTypesList = new ArrayList<String>();
		ArrayList<String> schemasList = new ArrayList<String>();
		for(Extension extension:extensions){
			String point = extension.getPoint();
		    switch (point){
	    		case EXTENSIONPOINT_SCHEMAS :
	    			try{
	    				List<Schema> schemaList = extension.getSchema();
	    				for(Schema schema : schemaList){
	    					String schemaName = schema.getName();
	    					//Schemas starting with var_ are reserved for worfklow tasks
	    					//Schemas ending with _cv are reserved for content views
	    					if(schemaName != null && !schemaName.startsWith("var_") && !schemaName.endsWith("_cv") && !schemasList.contains(schemaName)){
	    						result += schemaName+"_sh [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+schemaName+".ds\", label=\""+schemaName+"\",shape=box,fontcolor=white,color=\"#24A4CC\",fillcolor=\"#24A4CC\",style=\"filled\"];\n";
	    						if(nbSchemas > 0){
		    						schemas += "->";
		    					}
	    						schemas += schemaName+"_sh";
	    						schemasList.add(schemaName+"_sh");
	    						nbSchemas ++;
	    					}

	    				}
	    			}catch(Exception e){
	    				logger.error("Error when getting schemas", e);
	    			}
	    			break;
	    		case EXTENSIONPOINT_DOCTYPE :
	    			try{
	    				List<Doctype> docTypeList = extension.getDoctype();
	    				for(Doctype docType : docTypeList){
	    					String docTypeName = docType.getName();
	    					//DocType ending with _cv are created for content views
	    					if(docTypeName != null && !docTypeName.endsWith("_cv")){
	    						result += docTypeName+ " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+docTypeName+".doc\", label=\""+docTypeName+"\",shape=box,fontcolor=white,color=\"#1CA5FC\",fillcolor=\"#1CA5FC\",style=\"filled\"];\n";
	    						result += docTypeName+"->"+docType.getExtends()+"[label=\"inherits\"];\n";

	    						List<Doctype.Schema> extraSchemas = docType.getSchema();
	    						for(Doctype.Schema extraSchema: extraSchemas){
	    							//Don't include common schemas for the sake of visibility
	    							if(!COMMON_SCHEMAS.contains(extraSchema.getName())){
	    								result += docTypeName+"->"+extraSchema.getName()+"_sh;\n";
	    								if(!schemasList.contains(extraSchema.getName()+"_sh")){
		    								result += extraSchema.getName()+"_sh [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+extraSchema.getName()+".ds\", label=\""+extraSchema.getName()+"\",shape=box,fontcolor=white,color=\"#24A4CC\",fillcolor=\"#24A4CC\",style=\"filled\"];\n";
		    	    						if(nbSchemas > 0){
		    		    						schemas += "->";
		    		    					}
		    	    						schemas += extraSchema.getName()+"_sh";
		    	    						schemasList.add(extraSchema.getName()+"_sh");
		    	    						nbSchemas ++;
	    								}
	    							}
	    						}

	    						List<Doctype.Facet> extraFacets = docType.getFacet();
	    						for(Doctype.Facet extraFacet : extraFacets){
	    							result += docTypeName+"->"+extraFacet.getName()+"_facet;\n";
	    							if(!facets.contains(extraFacet.getName()+"_facet")){
		    							result += extraFacet.getName()+ "_facet [label=\""+extraFacet.getName()+"\",shape=box,fontcolor=white,color=\"#17384E\",fillcolor=\"#17384E\",style=\"filled\"];\n";
		    							if(nbFacets >0){
		    								facets += "->";
		    							}
		    							facets += extraFacet.getName()+"_facet";
		    							nbFacets ++;
		    						}
	    						}

	    						if(nbDocTypes > 0){
		    						docTypes += "->";
		    					}
	    						docTypes += docTypeName;
	    						nbDocTypes ++;

	    						if(!docTypesList.contains(docType.getExtends())){
	    							result += docType.getExtends()+ " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+docType.getExtends()+".doc\", label=\""+docType.getExtends()+"\",shape=box,fontcolor=white,color=\"#1CA5FC\",fillcolor=\"#1CA5FC\",style=\"filled\"];\n";
	    							docTypes += "->";
	    							docTypes += docType.getExtends();
	    							docTypesList.add(docType.getExtends());
	    							nbDocTypes ++;
	    						}
	    					}
	    				}
	    			}catch(Exception e){
	    				logger.error("Error when getting document type", e);
	    			}
	    			break;
	    	}
	    }

		schemas += (nbSchemas>1?" [style=invis]":"")+";\n}";
		docTypes += (nbDocTypes>1?" [style=invis]":"")+";\n}";
		facets += (nbFacets>1?" [style=invis]":"")+";\n}";

	    result += (nbSchemas>0?schemas:"")+"\n"+(nbDocTypes>0?docTypes:"")+"\n"+(nbFacets>0?facets:"")+"\n";
    	result += "}";

	    writeToFile(studiovizFolderPath+File.separator+File.separator+"inputModel.dot", result);

	    CmdParameters parameters = new CmdParameters();

	    //Generate png from dot
	    parameters.addNamedParameter("inputFile", studiovizFolderPath+File.separator+"inputModel.dot");
	    parameters.addNamedParameter("format", "png");
	    parameters.addNamedParameter("outputFile", destinationPath+File.separator+"imgModel.png");
	    commandLineExecutorComponent.execCommand("dot", parameters);

	    //Generate map from dot
	    parameters.addNamedParameter("format", "cmapx");
	    parameters.addNamedParameter("outputFile", destinationPath+File.separator+"imgModel.cmapx");
	    commandLineExecutorComponent.execCommand("dot", parameters);
	    map = FileUtils.readFileToString(new File(destinationPath+File.separator+"imgModel.cmapx"));
	    return map;
	}

	public String generateViewGraphFromXML(String studioProjectName, String destinationPath, String studiovizFolderPath, CommandLineExecutorComponent commandLineExecutorComponent, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException{
		JAXBContext jc = JAXBContext.newInstance("org.nuxeo.jaxb");
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		String result = "";
		String map = "";
		Component component = (Component) unmarshaller.unmarshal(new File(studiovizFolderPath+File.separator+"OSGI-INF"+File.separator+"extensions.xml"));

		String tabs = "subgraph cluster_0 {\n"+
		 		 	   "	style=\"dashed\";\n"+
		 		 	   " 	label = \"Tabs\";\n";

		String docTypes = "subgraph cluster_1 {\n"+
	 		 	   "	style=\"dashed\";\n"+
	 		 	   " 	label = \"Document Types\";\n";

		String contentViews = "subgraph cluster_2 {\n"+
	 		 	   "	style=\"dashed\";\n"+
	 		 	   " 	label = \"Content Views\";\n";
		
		String formLayouts = "subgraph cluster_3 {\n"+
	 		 	   "	style=\"dashed\";\n"+
	 		 	   " 	label = \"Form Layouts\";\n";

		result = "digraph V {\n"+
		    "graph [fontname = \"helvetica\", fontsize=11];\n"+
		    "node [fontname = \"helvetica\", fontsize=11];\n"+
		    "edge [fontname = \"helvetica\", fontsize=11];\n";
		List<Extension> extensions = component.getExtension();

		int nbTabs = 0;
		int nbDocTypes = 0;
		int nbContentViews = 0;
		int nbFormLayouts = 0;
		ArrayList<String> docTypesList = new ArrayList<String>();
		for(Extension extension:extensions){
			String point = extension.getPoint();
		    switch (point){
		    	case EXTENSIONPOINT_ACTIONS :
		    		try{
		    			List<Action> actions = extension.getAction();
		    			for(Action action:actions){
		    				String linkType = "";
		    				try{
		    					linkType = action.getType();
		    					//handle the rest_document_link types as Tabs
		    					if(linkType == null || !(linkType).equals("rest_document_link")){
		    						continue;
		    					}

		    				}catch(Exception e){
		    					logger.error("Error when getting chainId", e);
		    				}
		    				String cleanedActionId = cleanUpForDot(action.getId());

		    				Filter filter = action.getFilter();
		    				if(filter != null){
		    					List<Rule> rules = filter.getRule();
		    					if(rules != null){
		    						for(Rule rule: rules){
		    							if("true".equals(rule.getGrant())){
		    								List<Type> types = rule.getType();
		    								if(types !=null){
		    									for(Type type:types){
		    										String docTypeName = type.getValue();
		    					    				result += cleanedActionId+"_tab -> "+docTypeName+";\n";

		    					    				if(!docTypesList.contains(docTypeName)){
		    					    					result += docTypeName+ " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+docTypeName+".doc\", label=\""+docTypeName+"\",shape=box,fontcolor=white,color=\"#1CA5FC\",fillcolor=\"#1CA5FC\",style=\"filled\"];\n";
		    					    					if(nbDocTypes >0){
		    					    						docTypes += "->";
		    					    					}
		    		    								docTypes += docTypeName;
		    		    								docTypesList.add(docTypeName);
		    		    								nbDocTypes ++;
		    					    				}

		    									}
		    								}
		    							}
		    						}
		    					}
		    				}


		    				if(!tabs.contains(cleanedActionId)){
		    					result += cleanedActionId + "_tab [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+action.getId()+".tab\" label=\""+action.getId()+"\",shape=box,fontcolor=white,color=\"#2B333E\",fillcolor=\"#2B333E\",style=\"filled\"];\n";
		    					if(nbTabs >0){
			    					tabs += "->";
		    					}
		    					tabs += cleanedActionId+"_tab";
		    					nbTabs ++;
		    				}
		    			}
		    		}catch(Exception e){
		    			logger.error("Error when getting Actions", e);
		    		}
		    		break;

	    		case EXTENSIONPOINT_TYPES :
	    			try{
	    				List<org.nuxeo.jaxb.Component.Extension.Type> typeList = extension.getType();
	    				for(org.nuxeo.jaxb.Component.Extension.Type type : typeList){
	    					String typeId = type.getId();

	    					List<ContentViews> contentViewsList= type.getContentViews();
	    					if(contentViewsList != null){
	    						for(ContentViews cvs : contentViewsList){
	    							ContentView contentView = cvs.getContentView();
	    							if("content".equals(cvs.getCategory())){

	    								if(!docTypesList.contains(typeId)){
	    			    					result += typeId+ " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+typeId+".doc\", label=\""+typeId+"\",shape=box,fontcolor=white,color=\"#1CA5FC\",fillcolor=\"#1CA5FC\",style=\"filled\"];\n";
	    			    					if(nbDocTypes >0){
	    			    						docTypes += "->";
	    			    					}
	    									docTypes += typeId;
	    									docTypesList.add(typeId);
	    									nbDocTypes ++;
	    			    				}

	    								String contentViewName = contentView.getValue();
	    								result += typeId+"->"+contentViewName+";\n";
	    								if(!contentViews.contains(contentViewName)){
	    									result += contentViewName+ " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+contentViewName+".contentView\", label=\""+contentViewName+"\",shape=box,fontcolor=white,color=\"#31A3C5\",fillcolor=\"#31A3C5\",style=\"filled\"];\n";
		    		    					if(nbContentViews >0){
		    		    						contentViews += "->";
		    		    					}
		    		    					contentViews += contentViewName;
		    		    					nbContentViews ++;
	    								}
	    							}
	    						}
	    					}
	    						    					
	    					//Handle Form Layouts
	    					List<Layouts> layoutList = type.getLayouts();
	    					for(Layouts layouts: layoutList){
	    						Layout layout = layouts.getLayout();
	    						if(!layout.getValue().startsWith("layout@") && !typeId.endsWith("_cv")){
	    							String formLayoutName = layout.getValue().split("@")[0];	    							
	    							if(!formLayouts.contains(formLayoutName+"_fl")){
	    								
	    								if(!docTypesList.contains(typeId)){
	    			    					result += typeId+ " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+typeId+".doc\", label=\""+typeId+"\",shape=box,fontcolor=white,color=\"#1CA5FC\",fillcolor=\"#1CA5FC\",style=\"filled\"];\n";
	    			    					if(nbDocTypes >0){
	    			    						docTypes += "->";
	    			    					}
	    									docTypes += typeId;
	    									docTypesList.add(typeId);
	    									nbDocTypes ++;
	    			    				}
	    								
	    								result += typeId+"->"+formLayoutName+"_fl;\n";
    									result += formLayoutName+ "_fl [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+formLayoutName+".layout\", label=\""+formLayoutName+"\",shape=box,fontcolor=white,color=\"#FC4835\",fillcolor=\"#FC4835\",style=\"filled\"];\n";
	    		    					if(nbFormLayouts >0){
	    		    						formLayouts += "->";
	    		    					}
	    		    					formLayouts += formLayoutName+"_fl";
	    		    					nbFormLayouts ++;
    								}
	    						}
	    					}
	    					
	    				}
	    			}catch(Exception e){
	    				logger.error("Error when getting document type", e);
	    			}
	    			break;
	    	}
	    }

		tabs += (nbTabs>1?" [style=invis]":"")+";\n}";
		docTypes += (nbDocTypes>1?" [style=invis]":"")+";\n}";
		contentViews +=  (nbContentViews>1?" [style=invis]":"")+";\n}";
		formLayouts +=  (nbFormLayouts>1?" [style=invis]":"")+";\n}";
	    result += (nbTabs>0?tabs:"")+"\n"+(nbDocTypes>0?docTypes:"")+"\n"+(nbContentViews>0?contentViews:"")+"\n"+(nbFormLayouts>0?formLayouts:"")+"\n";
    	result += "}";

	    writeToFile(studiovizFolderPath+File.separator+File.separator+"inputView.dot", result);

	    CmdParameters parameters = new CmdParameters();

	    //Generate png from dot
	    parameters.addNamedParameter("inputFile", studiovizFolderPath+File.separator+"inputView.dot");
	    parameters.addNamedParameter("format", "png");
	    parameters.addNamedParameter("outputFile", destinationPath+File.separator+"imgView.png");
	    commandLineExecutorComponent.execCommand("dot", parameters);

	    //Generate map from dot
	    parameters.addNamedParameter("format", "cmapx");
	    parameters.addNamedParameter("outputFile", destinationPath+File.separator+"imgView.cmapx");
	    commandLineExecutorComponent.execCommand("dot", parameters);
	    map = FileUtils.readFileToString(new File(destinationPath+File.separator+"imgView.cmapx"));
	    return map;
	}

	public String generateBusinessRulesGraphFromXML(String studioProjectName, String destinationPath, String studiovizFolderPath, CommandLineExecutorComponent commandLineExecutorComponent, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException{
		JAXBContext jc = JAXBContext.newInstance("org.nuxeo.jaxb");
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		String result = "";
		String map = "";
		Component component = (Component) unmarshaller.unmarshal(new File(studiovizFolderPath+File.separator+"OSGI-INF"+File.separator+"extensions.xml"));

		String userActions = "subgraph cluster_0 {\n"+
						 	 "	style=\"dashed\";\n"+
						     "	label = \"User Actions\";\n";

		String automationChainsAndScripting = "subgraph cluster_1 {\n"+
				 							  "	style=\"dashed\";\n"+
				 							  " label = \"Automation Chains & Scriptings\";\n";

		String events =  "subgraph cluster_2 {\n"+
				 		 "	style=\"dashed\";\n"+
				 		 "  label = \"Events\";\n";
		
		String wfTasks =  "subgraph cluster_3 {\n"+
		 		 "	style=\"dashed\";\n"+
		 		 "  label = \"Workflow Tasks\";\n";

		result = "digraph BL {\n"+
		    "graph [fontname = \"helvetica\", fontsize=11];\n"+
		    "node [fontname = \"helvetica\", fontsize=11];\n"+
		    "edge [fontname = \"helvetica\", fontsize=11];\n";
		List<Extension> extensions = component.getExtension();
		String pattern = "\\#\\{operationActionBean.doOperation\\('(.*)'\\)\\}";
		// Create a Pattern object
		Pattern r = Pattern.compile(pattern);
		int nbUserActions = 0;
		int nbAutomationChains = 0;
		int nbAutomationScripting = 0;
		int nbEvents = 0;
		int nbWfTasks = 0;
		
		for(Extension extension:extensions){
			String point = extension.getPoint();
		    switch (point){
		    	case EXTENSIONPOINT_ACTIONS :
		    		try{
		    			List<Action> actions = extension.getAction();
		    			for(Action action:actions){
		    				String chainId = "";		    				
		    				try{
		    					chainId = action.getLink();
		    					if(chainId == null){
		    						continue;
		    					}
		    					// Now create matcher object.
		    				    Matcher m = r.matcher(chainId);
		    				    if (m.find( )) {
		    				    	chainId = m.group(1);
		    				    }
		    				}catch(Exception e){
		    					logger.error("Error when getting chainId", e);
		    				}
		    				String cleanedActionId = cleanUpForDot(action.getId());

		    				if(chainId != null && !("").equals(chainId) && !(".").equals(chainId)  && !chainId.endsWith("xhtml")){
		    					String cleanedChainId = cleanUpForDot(chainId);
		    					
		    					//contextual graph
		    					//skip this one if it's not in the list of Chains to display		 
		    					if(nodeList != null && !nodeList.contains(cleanedChainId)){
		    						continue;
		    					}
		    								    					
		    					String refChainId = chainId.startsWith("javascript.")? chainId.replace("javascript.", "")+".scriptedOperation" : chainId+".ops";
		    					result += cleanedActionId+"_action -> "+cleanedChainId+";\n";

			    				if(!automationList.contains(cleanedChainId)){
			    					result += cleanedChainId + " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+refChainId+"\", label=\""+chainId+"\",shape=box,fontcolor=white,color=\"#28A3C7\",fillcolor=\"#28A3C7\",style=\"filled\"];\n";
			    					if(nbAutomationChains >0 || nbAutomationScripting >0){
			    						automationChainsAndScripting += "->";
				    				}

			    					automationChainsAndScripting += cleanedChainId;
			    					automationList.add(cleanedChainId);
			    					if(chainId.startsWith("javascript")){
				    					nbAutomationScripting ++;
				    				}else{
				    					nbAutomationChains ++;
				    				}			    					
			    				}
			    				result += cleanedActionId+"_action [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+action.getId()+".action\", label=\""+action.getId()+"\n"+(action.getLabel()!= null ? action.getLabel():"")+"\",shape=box,fontcolor=white,color=\"#00ADFF\",fillcolor=\"#00ADFF\",style=\"filled\"];\n";
				    			if(nbUserActions >0){
				    				userActions += "->";
				    			}
				    			userActions += cleanedActionId+"_action";
				    			nbUserActions ++;
		    				}

		    			}
		    		}catch(Exception e){
		    			logger.error("Error when getting Actions", e);
		    		}
		    		break;
		    	case EXTENSIONPOINT_CHAIN :
		    		try{
		    			List<Chain> chains = extension.getChain();
		    			for(Chain chain:chains){
		    				String chainId = chain.getId();
		    				String refChainId = chainId.startsWith("javascript.")? chainId.replace("javascript.", "")+".scriptedOperation" : chainId+".ops";

		    				String chainIdForDot = cleanUpForDot(chain.getId());
		    						    				
		    				//contextual graph
	    					//skip this one if it's not in the list of Chains to display
		    				boolean mainChainIsPartOfTheNodeList = true;
		    				boolean secondChainIsPartOfTheNodeList = false;
	    					if(nodeList != null && !nodeList.contains(chainIdForDot)){
	    						mainChainIsPartOfTheNodeList = false;
	    					}
		    				
		    				//handle the link between 2 Automation chains
	    					if(chain.getOperation() != null){
	    						for(org.nuxeo.jaxb.Component.Extension.Chain.Operation operation:chain.getOperation()){
	    							if(("RunOperation").equals(operation.getId())){
	    								for(org.nuxeo.jaxb.Component.Extension.Chain.Operation.Param param : operation.getParam()){
	    									if(("id").equals(param.getName())){
	    										if(param.getValue().contains(":")){
	    											String exprPattern = "*\"(.*)\":\"(.*)";
	    											Pattern expR = Pattern.compile(exprPattern);
	    											Matcher m = expR.matcher(param.getValue());
	    						    				if (m.find( )) {
	    						    						    						    					
	    						    					if(nodeList == null || (nodeList != null && nodeList.contains(m.group(1))) || mainChainIsPartOfTheNodeList){
	    						    						String cleanedSecondChain = cleanUpForDot(m.group(1));
	    						    						result += chainIdForDot+" -> "+cleanedSecondChain+";\n";
	    						    						secondChainIsPartOfTheNodeList = true;
	    						    						if(!automationList.contains(cleanedSecondChain)){
				    											if(nbAutomationChains >0 || nbAutomationScripting >0){
				    					    						automationChainsAndScripting += "->";
				    						    				}
				    					    					automationChainsAndScripting += cleanedSecondChain;
				    					    					automationList.add(cleanedSecondChain);
				    					    					if(chainId.startsWith("javascript")){
				    						    					nbAutomationScripting ++;
				    						    				}else{
				    						    					nbAutomationChains ++;
				    						    				}	
				    					    					String refSecondChainId = m.group(1).startsWith("javascript.")? m.group(1).replace("javascript.", "")+".scriptedOperation" : m.group(1)+".ops";
										    					result += cleanedSecondChain + " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+refSecondChainId+"\", label=\""+m.group(1)+"\",shape=box,fontcolor=white,color=\"#28A3C7\",fillcolor=\"#28A3C7\",style=\"filled\"];\n";

			    											}	    
	    						    						
	    						    					}
	    						    					
	    						    					if(nodeList == null || (nodeList != null && nodeList.contains(m.group(2))) || mainChainIsPartOfTheNodeList){	    						    			
	    						    						String cleanedSecondChain = cleanUpForDot(m.group(2));
	    						    						result += chainIdForDot+" -> "+cleanedSecondChain+";\n";
	    						    						secondChainIsPartOfTheNodeList = true;
	    						    						if(!automationList.contains(cleanedSecondChain)){
				    											if(nbAutomationChains >0 || nbAutomationScripting >0){
				    					    						automationChainsAndScripting += "->";
				    						    				}
				    					    					automationChainsAndScripting += cleanedSecondChain;
				    					    					automationList.add(cleanedSecondChain);
				    					    					if(chainId.startsWith("javascript")){
				    						    					nbAutomationScripting ++;
				    						    				}else{
				    						    					nbAutomationChains ++;
				    						    				}
				    					    					String refSecondChainId = m.group(2).startsWith("javascript.")? m.group(2).replace("javascript.", "")+".scriptedOperation" : m.group(2)+".ops";
										    					result += cleanedSecondChain + " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+refSecondChainId+"\", label=\""+m.group(2)+"\",shape=box,fontcolor=white,color=\"#28A3C7\",fillcolor=\"#28A3C7\",style=\"filled\"];\n";

			    											}	
	    						    					}
	    						    					
	    						    				}
	    										}else{
	    											if(nodeList == null || (nodeList != null && nodeList.contains(cleanUpForDot(param.getValue()))) || mainChainIsPartOfTheNodeList){
	    												String cleanedSecondChain = cleanUpForDot(param.getValue());
	    												secondChainIsPartOfTheNodeList = true;
	    												result += chainIdForDot+" -> "+cleanedSecondChain+";\n";
		    											if(!automationList.contains(cleanedSecondChain)){
			    											if(nbAutomationChains >0 || nbAutomationScripting >0){
			    					    						automationChainsAndScripting += "->";
			    						    				}

			    					    					automationChainsAndScripting += cleanedSecondChain;
			    					    					automationList.add(cleanedSecondChain);
			    					    					if(chainId.startsWith("javascript")){
			    						    					nbAutomationScripting ++;
			    						    				}else{
			    						    					nbAutomationChains ++;
			    						    				}
			    					    					String refSecondChainId = param.getValue().startsWith("javascript.")? param.getValue().replace("javascript.", "")+".scriptedOperation" : param.getValue()+".ops";
									    					result += cleanedSecondChain + " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+refSecondChainId+"\", label=\""+param.getValue()+"\",shape=box,fontcolor=white,color=\"#28A3C7\",fillcolor=\"#28A3C7\",style=\"filled\"];\n";

		    											}	    												
	    											}
	    										}
	    									}
	    								}
	    							//handle the link between an Automation chain & scripting
	    							}else if(operation.getId().startsWith("javascript.")){
	    								if(nodeList == null || (nodeList != null && nodeList.contains(cleanUpForDot(operation.getId()))) || mainChainIsPartOfTheNodeList){
	    									String cleanedSecondChain = cleanUpForDot(operation.getId());
	    									secondChainIsPartOfTheNodeList = true;
		    								result += chainIdForDot+" -> "+cleanedSecondChain+";\n";
											if(!automationList.contains(cleanedSecondChain)){
												if(nbAutomationChains >0 || nbAutomationScripting >0){
						    						automationChainsAndScripting += "->";
							    				}
						    					automationChainsAndScripting += cleanedSecondChain;
						    					automationList.add(cleanedSecondChain);
							    				nbAutomationScripting ++;	
							    				
							    				String refSecondChainId = operation.getId().startsWith("javascript.")? operation.getId().replace("javascript.", "")+".scriptedOperation" : operation.getId()+".ops";
						    					result += cleanedSecondChain + " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+refSecondChainId+"\", label=\""+operation.getId()+"\",shape=box,fontcolor=white,color=\"#28A3C7\",fillcolor=\"#28A3C7\",style=\"filled\"];\n";

											}
	    								}
	    							}
	    						}
	    					}
	    					
	    					if(!mainChainIsPartOfTheNodeList && !secondChainIsPartOfTheNodeList){
	    						continue;
	    					}
	    					
	    					if(!automationList.contains(chainIdForDot)){
		    					if(nbAutomationChains >0 || nbAutomationScripting >0){
		    						automationChainsAndScripting += "->";
			    				}
	
		    					automationChainsAndScripting += chainIdForDot;
		    					automationList.add(chainIdForDot);
		    					if(chainId.startsWith("javascript")){
			    					nbAutomationScripting ++;
			    				}else{
			    					nbAutomationChains ++;
			    				}
		    					
		    					String description = (chain.getDescription() != null ? "\n"+chain.getDescription():"");
			    				description = description.replace("\"", "\\\"");
		    					result += chainIdForDot + " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+refChainId+"\", label=\""+chainId+description+"\",shape=box,fontcolor=white,color=\"#28A3C7\",fillcolor=\"#28A3C7\",style=\"filled\"];\n";
	    					}
	    				}
	    			}catch(Exception e){
	    				logger.error("Error when getting Chains", e);
	    			}
	    			break;
	    		case EXTENSIONPOINT_EVENT_HANDLERS :
	    			try{
	    				List<Handler> handlers = extension.getHandler();
	    				for(Handler handler:handlers){
	    					handler.getChainId();
	    					String chainIdForDot = cleanUpForDot(handler.getChainId());
	    					
	    					if(nodeList != null && !nodeList.contains(chainIdForDot)){
	    						continue;
	    					}

	    					result += chainIdForDot+"_handler"+ " [label=\""+handler.getChainId()+"_handler\",shape=box,fontcolor=white,color=\"#FF462A\",fillcolor=\"#FF462A\",style=\"filled\"];\n";
	    					result += chainIdForDot+"_handler"+" -> "+chainIdForDot+";\n";

	    					if(nbEvents > 0){
	    						events += "->";
	    					}
	    					events += cleanUpForDot(handler.getChainId())+"_handler";
	    					nbEvents ++;

	    					if(!automationList.contains(chainIdForDot)){
		    					if(nbAutomationChains >0 || nbAutomationScripting >0){
		    						automationChainsAndScripting += "->";
			    				}

		    					automationChainsAndScripting += chainIdForDot;
		    					automationList.add(chainIdForDot);
		    					if(chainIdForDot.startsWith("javascript")){
			    					nbAutomationScripting ++;
			    				}else{
			    					nbAutomationChains ++;
			    				}
		    					result += chainIdForDot+ " [label=\""+handler.getChainId()+"\",shape=box,fontcolor=white,color=\"#28A3C7\",fillcolor=\"#28A3C7\",style=\"filled\"];\n";
		    					
		    				}


	    				}
	    			}catch(Exception e){
	    				logger.error("Error when getting Event Handlers", e);
	    			}
	    			break;
	    		case EXTENSIONPOINT_ROUTE_MODEL_IMPORTER :
		    		List<TemplateResource> trList = extension.getTemplateResource();
		    		for(TemplateResource tr : trList){
		    			Runtime rt = Runtime.getRuntime();
		    		    String[] cmd = { "/bin/sh", "-c", "cd "+studiovizFolderPath+File.separator+"data; jar xf "+tr.getPath().replace("data/", "") };    
		    		    Process p = rt.exec(cmd);
		    		    try {
		    				p.waitFor();
		    			} catch (InterruptedException e) {
		    				logger.error("Error while unzipping ["+tr.getPath()+"]");
		    			}			
		    		    
		    		    //Get all the tasks under the Workflow folder
		    		    File file = new File(studiovizFolderPath+File.separator+"data"+File.separator+tr.getId());
		    		    
		    			String[] tasks = file.list(new FilenameFilter() {
		    			  @Override
		    			  public boolean accept(File current, String name) {
		    			    return new File(current, name).isDirectory();	    
		    			  }
		    			});
		    			
		    			if(tasks != null){
			    			for(String task: tasks){			    				
			    				//Use task as the id of the node			    				
				    			//Read the content of the document.xml
				    			String xmlText = FileUtils.readFileToString(new File(studiovizFolderPath+File.separator+"data"+File.separator+tr.getId()+File.separator+task+File.separator+"document.xml"));
				    			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				    			factory.setNamespaceAware(true);
				    			DocumentBuilder db;
								try {
									db = factory.newDocumentBuilder();
									InputStream in = new ByteArrayInputStream(xmlText.getBytes("UTF-8"));
					    			Document doc = db.parse(in);
					    			
					    			//title
					    			String title = "";
					    			NodeList nodeTitle = doc.getElementsByTagName("dc:title");
					    			if(nodeTitle != null && nodeTitle.getLength()>0){
					    				title = nodeTitle.item(0).getTextContent();
					    			}
					    			
					    			//description
					    			String desc = "";
					    			NodeList nodeDesc = doc.getElementsByTagName("dc:description");
					    			if(nodeDesc != null && nodeDesc.getLength()>0){
					    				desc = nodeDesc.item(0).getTextContent();
					    			}
					    			
					    			//inputChain
					    			String inputChain = "";
					    			NodeList nodeIC = doc.getElementsByTagName("rnode:inputChain");
					    			if(nodeIC != null && nodeIC.getLength()>0){
					    				inputChain = cleanUpForDot(nodeIC.item(0).getTextContent());
					    				
					    			}
					    			
					    			//outputChain
					    			String outputChain = "";
					    			NodeList nodeOC = doc.getElementsByTagName("rnode:outputChain");
					    			if(nodeOC != null && nodeOC.getLength()>0){
					    				outputChain = cleanUpForDot(nodeOC.item(0).getTextContent());
					    			}
					    			
					    			if(nodeList != null && !nodeList.contains(inputChain) && !nodeList.contains(outputChain)){
			    						continue;
			    					}
					    			
					    			if(!("").equals(inputChain) || !("").equals(outputChain)){
					    				if(nodeList == null || (nodeList != null && nodeList.contains(inputChain))){
						    				if(!("").equals(inputChain)){
						    					if(!automationList.contains(inputChain)){
							    					if(nbAutomationChains >0 || nbAutomationScripting >0){
							    						automationChainsAndScripting += "->";
								    				}
							    					if(inputChain.startsWith("javascript")){
								    					nbAutomationScripting ++;
								    				}else{
								    					nbAutomationChains ++;
								    				}
							    					automationChainsAndScripting += inputChain;
							    					automationList.add(inputChain);
						    					
							    					String refChainId = inputChain.startsWith("javascript.")? inputChain.replace("javascript.", "")+".scriptedOperation" : inputChain+".ops";
							    					result += inputChain + " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+refChainId+"\", label=\""+inputChain+"\",shape=box,fontcolor=white,color=\"#28A3C7\",fillcolor=\"#28A3C7\",style=\"filled\"];\n";
						    					}
							    				result += task + " -> "+ inputChain+";\n";
						    				}
					    				}
					    				if(nodeList == null || (nodeList != null && nodeList.contains(outputChain))){
						    				if(!("").equals(outputChain)){
						    					if(!automationList.contains(outputChain)){
							    					if(nbAutomationChains >0 || nbAutomationScripting >0){
							    						automationChainsAndScripting += "->";
								    				}
							    					if(outputChain.startsWith("javascript")){
								    					nbAutomationScripting ++;
								    				}else{
								    					nbAutomationChains ++;
								    				}
							    					automationChainsAndScripting += outputChain;
							    					automationList.add(outputChain);
						    					
							    					String refChainId = outputChain.startsWith("javascript.")? outputChain.replace("javascript.", "")+".scriptedOperation" : outputChain+".ops";
							    					result += outputChain + " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+refChainId+"\", label=\""+outputChain+"\",shape=box,fontcolor=white,color=\"#28A3C7\",fillcolor=\"#28A3C7\",style=\"filled\"];\n";
						    					}
							    				result += task + " -> "+ outputChain+";\n";
						    				}
					    				}
					    				
					    				/*if(nodeList == null || (nodeList != null && nodeList.contains(outputChain)) || (nodeList != null && nodeList.contains(inputChain))){
					    					
					    				}*/
					    				
					    				if(nodeList == null || (nodeList != null && nodeList.contains(outputChain)) || (nodeList != null && nodeList.contains(inputChain))){
						    				if(!wfTasks.contains(task)){
						    					String taskName = tr.getId()+"\n"+title+ (!desc.equals("")? "\n"+desc :"");
						    					result += task + " [URL=\""+CONNECT_URL+studioProjectName+"#@feature:"+tr.getId()+".workflow\", label=\""+taskName+"\",shape=box,fontcolor=white,color=\"#1BB249\",fillcolor=\"#1BB249\",style=\"filled\"];\n";
						    					if(nbWfTasks>0){
						    						wfTasks += "->"; 
						    					}
						    					wfTasks += task;
						    					nbWfTasks ++;
						    				}
					    				}
					    				
					    			}					    			
								} catch (ParserConfigurationException e) {
									logger.error("Error while getting Worflow Tasks",e);
								} catch (SAXException e) {
									logger.error("Error while getting Worflow Tasks",e);
								}
			    			}
		    			}			    						    			
		    		}
		    		break;
	    	}
	    }

		userActions += (nbUserActions>1?" [style=invis]":"")+";\n}";
		automationChainsAndScripting += (nbAutomationChains>1?" [style=invis]":"")+";\n}";
		events += (nbEvents>1?" [style=invis]":"")+";\n}";
		wfTasks += (nbWfTasks>1?" [style=invis]":"")+";\n}";
		
	    result += (nbUserActions>0 ? userActions: "")+"\n"+((nbAutomationChains+ nbAutomationScripting >0)? automationChainsAndScripting:"")+"\n"+(nbEvents>0? events: "")+"\n"+(nbWfTasks>0? wfTasks: "")+"\n";
    	result += "}";

	    writeToFile(studiovizFolderPath+File.separator+File.separator+"inputBusinessRules.dot", result);

	    CmdParameters parameters = new CmdParameters();

	    //Generate png from dot
	    parameters.addNamedParameter("inputFile", studiovizFolderPath+File.separator+"inputBusinessRules.dot");
	    parameters.addNamedParameter("format", "png");
	    parameters.addNamedParameter("outputFile", destinationPath+File.separator+"imgBusinessRules.png");
	    commandLineExecutorComponent.execCommand("dot", parameters);

	    //Generate map from dot
	    parameters.addNamedParameter("format", "cmapx");
	    parameters.addNamedParameter("outputFile", destinationPath+File.separator+"imgBusinessRules.cmapx");
	    commandLineExecutorComponent.execCommand("dot", parameters);
	    map = FileUtils.readFileToString(new File(destinationPath+File.separator+"imgBusinessRules.cmapx"));
	    return map;
	}

}