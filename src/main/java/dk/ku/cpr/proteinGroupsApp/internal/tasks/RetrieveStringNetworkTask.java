package dk.ku.cpr.proteinGroupsApp.internal.tasks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;

import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.group.CyGroup;
import org.cytoscape.group.CyGroupFactory;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.ProvidesTitle;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskMonitor.Level;
import org.cytoscape.work.TaskObserver;
import org.cytoscape.work.json.JSONResult;
import org.cytoscape.work.util.ListSingleSelection;

import dk.ku.cpr.proteinGroupsApp.internal.model.AppManager;
import dk.ku.cpr.proteinGroupsApp.internal.model.SharedProperties;
import dk.ku.cpr.proteinGroupsApp.internal.utils.SwingUtil;

public class RetrieveStringNetworkTask extends AbstractTask implements TaskObserver, ObservableTask {
	protected AppManager manager;

	protected String protected_query;
	protected String protected_netName;
	protected Integer protected_taxonID;
	protected String protected_species;
	protected double protected_cutoff;
	protected ListSingleSelection<String> protected_netType;
	protected HashMap<String, List<String>> protected_pg2proteinsMap;
	protected HashMap<String, Set<String>> protected_protein2pgsMap;

	protected CyNetwork retrievedNetwork;

	private boolean isGUI;

	public RetrieveStringNetworkTask(AppManager manager) {
		super();
		this.manager = manager;

		this.protected_query = "";
		this.protected_netName = "";
		this.protected_taxonID = null;
		this.protected_species = null;
		this.protected_cutoff = 0.4;
		// TODO: move names to sharedproperties
		this.protected_netType = new ListSingleSelection<String>(
				Arrays.asList("full STRING network", "physical subnetwork"));
		this.protected_netType.setSelectedValue("full STRING network");
		this.protected_pg2proteinsMap = null;
		this.protected_protein2pgsMap = null;

		this.isGUI = false;
	}

	public void setQuery(String query) {
		this.protected_query = query;
	}

	public void setNetworkName(String name) {
		this.protected_netName = name;
	}

	public void setTaxonID(Integer taxonID) {
		this.protected_taxonID = taxonID;
	}

	public void setSpecies(String species) {
		this.protected_species = species;
	}

	public void setCutoff(double cutoff) {
		this.protected_cutoff = cutoff;
	}

	public void setNetType(String netType) {
		this.protected_netType.setSelectedValue(netType);
	}

	public void setIsGUI(boolean isGUI) {
		this.isGUI = isGUI;
	}

	public void setPGMapping(HashMap<String, List<String>> pg2proteinsMap) {
		this.protected_pg2proteinsMap = pg2proteinsMap;
	}

	public void setProteinMapping(HashMap<String, Set<String>> protein2pgsMap) {
		this.protected_protein2pgsMap = protein2pgsMap;
	}

	@ProvidesTitle
	public String getName() {
		return "Retrieve STRING Network";
	}

	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		taskMonitor.setTitle(this.getName());

		taskMonitor.setStatusMessage("Query: " + this.protected_query);
		taskMonitor.setStatusMessage("New network name: " + this.protected_netName);
		taskMonitor.setStatusMessage("Taxon ID: " + this.protected_taxonID);
		taskMonitor.setStatusMessage("Species: " + this.protected_species);
		taskMonitor.setStatusMessage("Cut-off: " + this.protected_cutoff);
		taskMonitor.setStatusMessage("Network type: " + this.protected_netType.getSelectedValue());

		if ((this.protected_taxonID == null) && (this.protected_species == null)) {
			taskMonitor.setStatusMessage("You have to give either the Taxon ID or the Species name.");
			return;
		}

		// We set the arguments for the STRING command
		Map<String, Object> args = new HashMap<>();
		args.put("query", this.protected_query);
		if (this.protected_taxonID != null) {
			args.put("taxonID", this.protected_taxonID);
		}
		if (this.protected_species != null) {
			args.put("species", this.protected_species);
		}
		args.put("cutoff", String.valueOf(this.protected_cutoff));
		args.put("networkType", protected_netType.getSelectedValue());
		args.put("limit", "0");
		args.put("newNetName", this.protected_netName);

		// We call the STRING command
		StringCommandTaskFactory factory = new StringCommandTaskFactory(this.manager,
				SharedProperties.STRING_CMD_PROTEIN_QUERY, args, this);
		TaskIterator ti = factory.createTaskIterator();
		this.manager.executeSynchronousTask(ti, this);

		// the STRING command is executed synchronously, so we can check the result
		if (this.getResults(CyNetwork.class) == null) {
			// If there is not result: we display an error message.
			taskMonitor.showMessage(Level.ERROR, "No network was retrieved.");

			if (this.isGUI) {
				// We have to invoke it on another thread because showMessageDialog blocks the
				// main process
				SwingUtil.invokeOnEDT(new Runnable() {
					@Override
					public void run() {
						JOptionPane.showMessageDialog(manager.getService(CySwingApplication.class).getJFrame(),
								"No network was retrieved.\nThe stringApp could not retrieve the queried network.",
								"Error while retrieving STRING network", JOptionPane.ERROR_MESSAGE);
					}
				});
			}
		}
	}

	@Override
	public void taskFinished(ObservableTask task) {
		if (task.getClass().getSimpleName().equals("ProteinQueryTask")) {
			retrievedNetwork = task.getResults(CyNetwork.class);

			// create a map of query term to node
			HashMap<String, CyNode> queryTerm2node = new HashMap<String, CyNode>();
			for (CyNode node : retrievedNetwork.getNodeList()) {
				String queryTerm = retrievedNetwork.getRow(node).get("query term", String.class);
				if (queryTerm != null) {
					queryTerm2node.put(queryTerm, node);
				}
			}

			// add needed columns
			manager.createBooleanColumnIfNeeded(retrievedNetwork.getDefaultNodeTable(), Boolean.class,
					SharedProperties.USE_ENRICHMENT, false);

			// duplicate nodes belonging to more than one protein group before creating the
			// groups?!?
			for (String protein : protected_protein2pgsMap.keySet()) {
				Set<String> proteinGroups = protected_protein2pgsMap.get(protein);
				if (proteinGroups.size() == 1) // ignore if there is a 1-to-1 mapping between protein and protein group
					continue;
				if (!queryTerm2node.containsKey(protein)) // ignore if we did not get a node from STRING for this ID
					continue;
				CyNode proteinNode = queryTerm2node.get(protein);
				for (int i = 0; i < proteinGroups.size(); i++) {
					// TODO: duplicate nodes belonging to different groups before creating the
					// groups?!?
					// TODO: we need some way to keep track of that... hmmm
				}
			}

			// find all PGs with more than one node and create group nodes for them
			CyGroupFactory groupFactory = manager.getService(CyGroupFactory.class);
			// TODO: fix some group setting here before creating the nodes?
			List<CyGroup> groups = new ArrayList<CyGroup>();
			for (String pg : protected_pg2proteinsMap.keySet()) {
				List<String> proteins = protected_pg2proteinsMap.get(pg);
				if (proteins.size() == 1) {
					// set the use for enrichment flag and continue with the others
					if (queryTerm2node.containsKey(proteins.get(0))) {
						retrievedNetwork.getRow(queryTerm2node.get(proteins.get(0)))
								.set(SharedProperties.USE_ENRICHMENT, true);
					}
					continue;
				}
				List<CyNode> nodes = new ArrayList<>();
				CyNode reprNode = null;
				for (String protein : proteins) {
					if (queryTerm2node.containsKey(protein)) {
						nodes.add(queryTerm2node.get(protein));
						// TODO: decide how to choose the repr node
						if (reprNode == null)
							reprNode = queryTerm2node.get(protein);
					}
				}
				// create and save group
				CyGroup pgGroup = groupFactory.createGroup(this.retrievedNetwork, nodes, null, true);
				groups.add(pgGroup);
				CyNode groupNode = pgGroup.getGroupNode();
				// TODO: set whatever attributes we need to set here...

				// set display name and query term to be the name as the pg
				retrievedNetwork.getRow(groupNode).set(SharedProperties.QUERYTERM, pg);
				retrievedNetwork.getRow(groupNode).set(SharedProperties.DISPLAY, pg); // for now, but probably better to
																						// concatenate this one

				// set group node to be used for enrichment with the string ID of the repr node
				retrievedNetwork.getRow(groupNode).set(SharedProperties.USE_ENRICHMENT, true);

				// set some attributes to be the name of the representative node
				// name, database identifier (string id), @id, namespace, node type, species,
				// imageurl, style, enhanced label
				retrievedNetwork.getRow(groupNode).set(CyNetwork.NAME,
						retrievedNetwork.getRow(reprNode).get(CyNetwork.NAME, String.class));
				retrievedNetwork.getRow(groupNode).set(SharedProperties.STRINGID,
						retrievedNetwork.getRow(reprNode).get(SharedProperties.STRINGID, String.class));
				retrievedNetwork.getRow(groupNode).set(SharedProperties.ID,
						retrievedNetwork.getRow(reprNode).get(SharedProperties.ID, String.class));
				retrievedNetwork.getRow(groupNode).set(SharedProperties.NAMESPACE,
						retrievedNetwork.getRow(reprNode).get(SharedProperties.NAMESPACE, String.class));
				retrievedNetwork.getRow(groupNode).set(SharedProperties.TYPE,
						retrievedNetwork.getRow(reprNode).get(SharedProperties.TYPE, String.class));
				retrievedNetwork.getRow(groupNode).set(SharedProperties.SPECIES,
						retrievedNetwork.getRow(reprNode).get(SharedProperties.SPECIES, String.class));
				retrievedNetwork.getRow(groupNode).set(SharedProperties.IMAGE,
						retrievedNetwork.getRow(reprNode).get(SharedProperties.IMAGE, String.class));
				//retrievedNetwork.getRow(groupNode).set(SharedProperties.STYLE,
				//		retrievedNetwork.getRow(reprNode).get(SharedProperties.STYLE, String.class));
				// retrievedNetwork.getRow(groupNode).set(SharedProperties.STYLE, "string:");
				retrievedNetwork.getRow(groupNode).set(SharedProperties.ELABEL_STYLE,
						retrievedNetwork.getRow(reprNode).get(SharedProperties.ELABEL_STYLE, String.class));

				// TODO: concatenate these:
				// canonical name, description, display, structures, dev. level, family,

				// TODO: average those
				// all compartment cols, all tissue cols, interactor score?

				// TODO: what to do with edge attributes?

			}
			// collapse groups...
			// TODO: turn off aggregation!
			// TODO: figure out in which order to do that since it changes the results
			// for (CyGroup group : groups) {
			// group.collapse(retrievedNetwork);
			// }
		}
	}

	@Override
	public void allFinished(FinishStatus finishStatus) {
		// Do nothing
	}

	@SuppressWarnings("unchecked")
	public <R> R getResults(Class<? extends R> clzz) {
		if (clzz.equals(CyNetwork.class)) {
			return (R) this.retrievedNetwork;
		} else if (clzz.equals(Long.class)) {
			if (this.retrievedNetwork == null)
				return null;
			return (R) this.retrievedNetwork.getSUID();
			// We need to use the actual class rather than the interface so that
			// CyREST can inspect it to find the annotations
		} else if (clzz.equals(JSONResult.class)) {
			return (R) ("{\"SUID\":" + this.retrievedNetwork.getSUID() + "}");
		} else if (clzz.equals(String.class)) {
			if (this.retrievedNetwork == null) {
				return (R) "No network was loaded";
			}
			return (R) this.retrievedNetwork.getSUID().toString();
		}
		return null;
	}

	public List<Class<?>> getResultClasses() {
		return Arrays.asList(JSONResult.class, String.class, Long.class, CyNetwork.class);
	}
}