package dk.ku.cpr.proteoVisualizer.internal.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.swing.ImageIcon;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.swing.search.NetworkSearchTaskFactory;
import org.cytoscape.command.AvailableCommands;
import org.cytoscape.command.CommandExecutorTaskFactory;
import org.cytoscape.group.CyGroup;
import org.cytoscape.group.CyGroupManager;
import org.cytoscape.group.CyGroupSettingsManager;
import org.cytoscape.group.CyGroupSettingsManager.DoubleClickAction;
import org.cytoscape.group.CyGroupSettingsManager.GroupViewType;
import org.cytoscape.group.events.GroupAboutToCollapseEvent;
import org.cytoscape.group.events.GroupAboutToCollapseListener;
import org.cytoscape.group.events.GroupCollapsedEvent;
import org.cytoscape.group.events.GroupCollapsedListener;
import org.cytoscape.group.events.GroupEdgesAddedEvent;
import org.cytoscape.group.events.GroupEdgesAddedListener;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyEdge.Type;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.events.SelectedNodesAndEdgesEvent;
import org.cytoscape.model.events.SelectedNodesAndEdgesListener;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.property.CyProperty;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.view.layout.CyLayoutAlgorithm;
import org.cytoscape.view.layout.CyLayoutAlgorithmManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.View;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TaskObserver;
import org.cytoscape.work.TunableSetter;

import dk.ku.cpr.proteoVisualizer.internal.tasks.StringPGSearchTaskFactory;

public class AppManager implements GroupAboutToCollapseListener, GroupCollapsedListener, GroupEdgesAddedListener, SelectedNodesAndEdgesListener {

	private CyServiceRegistrar serviceRegistrar;
	final AvailableCommands availableCommands;
	final CommandExecutorTaskFactory ceTaskFactory;

	public AppManager(CyServiceRegistrar serviceRegistrar) {
		this.serviceRegistrar=serviceRegistrar;
		this.availableCommands = serviceRegistrar.getService(AvailableCommands.class);
		this.ceTaskFactory = serviceRegistrar.getService(CommandExecutorTaskFactory.class);
	}

	/**
	 * Returns the specific queried service.
	 * @param clazz The class defining the type of service desired.
	 * @return A reference to a service of type <code>clazz</code>.
	 * 
	 * @throws RuntimeException If the requested service can't be found.
	 */
	public <T> T getService(Class<? extends T> clazz) {
		return this.serviceRegistrar.getService(clazz);
	}

	/**
	 * A method that attempts to get a service of the specified type and that passes the specified filter.
	 * If an appropriate service is not found, an exception will be thrown.
	 * @param clazz The class defining the type of service desired.
	 * @param filter The string defining the filter the service must pass. See OSGi's service filtering syntax for more detail.
	 * @return A reference to a service of type <code>serviceClass</code> that passes the specified filter.
	 * 
	 * @throws RuntimeException If the requested service can't be found.
	 */
	public <T> T getService(Class<? extends T> clazz, String filter) {
		return this.serviceRegistrar.getService(clazz, filter);
	}

	/**
	 * Registers an object as an OSGi service with the specified service interface and properties.
	 * @param service The object to be registered as a service.
	 * @param clazz The service interface the object should be registered as.
	 * @param props The service properties.
	 */
	public void registerService(Object service, Class<?> clazz, Properties props) {
		this.serviceRegistrar.registerService(service, clazz, props);
	}

	/**
	 * This method registers an object as an OSGi service for all interfaces that the object implements and with the specified properties.
	 * Note that this method will NOT register services for any packages with names that begin with "java", which is an effort to avoid registering meaningless services for core Java APIs.
	 * @param service The object to be registered as a service for all interfaces that the object implements.
	 * @param props The service properties.
	 */
	public void registerAllServices(CyProperty<Properties> service, Properties props) {
		this.serviceRegistrar.registerAllServices(service, props);
	}

	/**
	 * This method unregisters an object as an OSGi service for the specified service interface.
	 * @param service The object to be unregistered as a service.
	 * @param clazz The service interface the object should be unregistered as.
	 */
	public void unregisterService(Object service, Class<?> clazz) {
		this.serviceRegistrar.unregisterService(service, clazz);
	}

	/**
	 * This method unregisters an object as all OSGi service interfaces that the object implements.
	 * @param service The object to be unregistered for services it provides.
	 */
	public void unregisterAllServices(Object service) {
		this.serviceRegistrar.unregisterAllServices(service);
	}

	/**
	 * Executes a list of tasks in a synchronous way.
	 * @param ti The list of tasks to execute.
	 * @param to The class that listens to the result of the tasks.
	 */
	public void executeSynchronousTask(TaskIterator ti, TaskObserver to) {
		SynchronousTaskManager<?> taskM = this.serviceRegistrar.getService(SynchronousTaskManager.class);
		taskM.execute(ti, to);
	}

	/**
	 * Executes a list of tasks in a synchronous way.
	 * @param ti The list of tasks to execute.
	 */
	public void executeSynchronousTask(TaskIterator ti) {
		this.executeSynchronousTask(ti, null);
	}

	/**
	 * Executes a list of tasks in an asynchronous way.
	 * @param ti The list of tasks to execute.
	 * @param to The class that listens to the result of the tasks.
	 */
	public void executeTask(TaskIterator ti, TaskObserver to) {
		TaskManager<?, ?> taskM = this.serviceRegistrar.getService(TaskManager.class);
		taskM.execute(ti, to);
	}
	/**
	 * Executes a list of tasks in an asynchronous way.
	 * @param ti The list of tasks to execute.
	 */
	public void executeTask(TaskIterator ti) {
		this.executeTask(ti, null);
	}

	public void executeCommand(String namespace, String command, Map<String, Object> args, boolean synchronous) {
		executeCommand(namespace, command, args, null, synchronous);
	}

	public void executeCommand(String namespace, String command, Map<String, Object> args) {
		executeCommand(namespace, command, args, null, false);
	}

	public void executeCommand(String namespace, String command, Map<String, Object> args, TaskObserver observer,
			boolean synchronous) {
		if (!haveCommand(namespace, command)) {
			//LogUtils.warn("Command " + namespace + " " + command + " isn't available");
			return;
		}

		if (args == null)
			args = new HashMap<>();

		if (synchronous)
			executeSynchronousTask(ceTaskFactory.createTaskIterator(namespace, command, args, observer));
		else
			executeTask(ceTaskFactory.createTaskIterator(namespace, command, args, observer));
	}
	
	public boolean haveNamespace(String namespace) {
		return availableCommands.getNamespaces().contains(namespace);
	}
	
	public boolean haveCommand(String namespace, String command) {
		if (!haveNamespace(namespace)) {
			return false;
		}
		return availableCommands.getCommands(namespace).contains(command);
	}
	
	public ImageIcon getPVImageIcon() {
	    return new ImageIcon(getClass().getResource("/logo_trial_4_icon.png"));
	    //if (imageicon.getIconWidth() > 36) {
		//	return new ImageIcon(imageicon.getImage().getScaledInstance(-1, 26, Image.SCALE_DEFAULT));
		//} else {
		//	return imageicon;
		//}
	}
	
	public void setGroupSettings() {
		// Set some properties to the groups app
		CyGroupSettingsManager groupSettingsManager = getService(CyGroupSettingsManager.class);
		groupSettingsManager.setGroupViewType(GroupViewType.COMPOUND);
		groupSettingsManager.setEnableAttributeAggregation(false);
		groupSettingsManager.setDoubleClickAction(DoubleClickAction.EXPANDCONTRACT);
	}

	public void registerSearchTaskFactories() {
		StringPGSearchTaskFactory stringSearch = new StringPGSearchTaskFactory(this, getPVImageIcon());
		Properties propsSearch = new Properties();
		registerService(stringSearch, NetworkSearchTaskFactory.class, propsSearch);
	}

	
	public void createBooleanColumnIfNeeded(CyTable table, Class<?> clazz, String columnName, Boolean defaultValue) {
		if (table.getColumn(columnName) != null)
			return;

		table.createColumn(columnName, clazz, false, defaultValue);
	}

	public void createDoubleColumnIfNeeded(CyTable table, Class<?> clazz, String columnName, Double defaultValue) {
		if (table.getColumn(columnName) != null)
			return;

		table.createColumn(columnName, clazz, false, defaultValue);
	}

	public void createIntegerColumnIfNeeded(CyTable table, Class<?> clazz, String columnName, Integer defaultValue) {
		if (table.getColumn(columnName) != null)
			return;

		table.createColumn(columnName, clazz, false, defaultValue);
	}

	public void createStringColumnIfNeeded(CyTable table, Class<?> clazz, String columnName, String defaultValue) {
		if (table.getColumn(columnName) != null)
			return;

		table.createColumn(columnName, clazz, false, defaultValue);
	}

	public void createListColumnIfNeeded(CyTable table, Class<?> clazz, String columnName) {
		if (table.getColumn(columnName) != null)
			return;

		table.createListColumn(columnName, clazz, false);
	}

	public CyNetworkView getCurrentNetworkView() {
		return getService(CyApplicationManager.class).getCurrentNetworkView();
	}

	public CyNetwork getCurrentNetwork() {
		return getService(CyApplicationManager.class).getCurrentNetwork();
	}

	@Override
	public void handleEvent(GroupCollapsedEvent e) {
		CyGroup group = e.getSource();
		CyNode groupNode = group.getGroupNode();
		CyNetwork network = e.getNetwork();

		// do edge attribute aggregation for the stringdb namespace columns
		Collection<CyColumn> stringdbCols = network.getDefaultEdgeTable().getColumns(SharedProperties.STRINGDB_NAMESPACE);
		List<String> edgeColsToAggregate = new ArrayList<String>();
		for (CyColumn col : stringdbCols) {
			if (col == null || !col.getType().equals(Double.class)) 
				continue;
			edgeColsToAggregate.add(col.getName());
		}

		// TODO: do we need to check if string network, and if not ignore the event
		// CyNetworkView view = getCurrentNetworkView();
		// View<CyNode> nodeView = view.getNodeView(groupNode);
		if (e.collapsed()) {
			// System.out.println("group collapsed");
			// change style of string node
			if (network.getDefaultNodeTable().getColumn(SharedProperties.STYLE) != null)
				network.getRow(groupNode).set(SharedProperties.STYLE, "string:");
			//nodeView.setVisualProperty(BasicVisualLexicon.NODE_TRANSPARENCY, 255);
			
			// aggregate edge attributes if not already done
			List<CyEdge> groupNodeEdges = network.getAdjacentEdgeList(group.getGroupNode(), Type.ANY);
			for (CyEdge newEdge : groupNodeEdges) {
				Boolean edgeTypeMeta = group.getRootNetwork().getRow(newEdge, CyNetwork.HIDDEN_ATTRS).get("__isMetaEdge", Boolean.class);
				Boolean edgeAggregated = group.getRootNetwork().getRow(newEdge).get(SharedProperties.EDGEAGGREGATED, Boolean.class);
				// ignore edge if it is NOT a meta edge or if we already aggregated the attributes for it
				if (edgeTypeMeta == null || !edgeTypeMeta || (edgeAggregated != null && edgeAggregated)
						|| network.getDefaultEdgeTable().getColumn(SharedProperties.SCORE) == null)
					continue;
				// System.out.println("aggregate edge attributes for edge with SUID " + newEdge.getSUID());
				// find the neighbor
				CyNode neighbor = null;
				if (group.getGroupNode().equals(newEdge.getSource())) 
					neighbor = newEdge.getTarget();
				else 
					neighbor = newEdge.getSource();
				// aggregate edge attributes 
				aggregateGroupEdgeAttributes(network, group, newEdge, group.getNodeList(), neighbor, edgeColsToAggregate);
			}

		} else {
			// System.out.println("group expanded");
			// change style of string node
			if (network.getDefaultNodeTable().getColumn(SharedProperties.STYLE) != null)
				network.getRow(groupNode).set(SharedProperties.STYLE, "");
			//nodeView.setVisualProperty(BasicVisualLexicon.NODE_TRANSPARENCY, 50);
			
			// aggregate edge attributes if not already done 
			// get all external edges, includes both meta edges and edges from any node in the group to any other node in the network
			// not sure why we cannot get the neighbors using network.getAdjacentEdgeList(group.getGroupNode(), Type.ANY);
			Set<CyEdge> externalEdges = group.getExternalEdgeList();
			for (CyEdge newEdge : externalEdges) {
				Boolean edgeTypeMeta = group.getRootNetwork().getRow(newEdge, CyNetwork.HIDDEN_ATTRS).get("__isMetaEdge", Boolean.class);
				Boolean edgeAggregated = group.getRootNetwork().getRow(newEdge).get(SharedProperties.EDGEAGGREGATED, Boolean.class);
				// ignore edge if it is NOT a meta edge or if we already aggregated the attributes for it
				if (edgeTypeMeta == null || !edgeTypeMeta || (edgeAggregated != null && edgeAggregated)
						|| network.getDefaultEdgeTable().getColumn(SharedProperties.SCORE) == null)
					continue;
				// System.out.println("aggregate edge attributes for edge with SUID " + newEdge.getSUID());
				CyNode source = newEdge.getSource();
				CyNode target = newEdge.getTarget();
				if (group.getNodeList().contains(source)) {
					aggregateGroupEdgeAttributes(network, group, newEdge, new ArrayList<CyNode>(Arrays.asList(source)), target, edgeColsToAggregate);					
				} else if (group.getNodeList().contains(target)) {
					aggregateGroupEdgeAttributes(network, group, newEdge, new ArrayList<CyNode>(Arrays.asList(target)), source, edgeColsToAggregate);
				} 
				//else {
				//	System.out.println("neither source nor target is a node in the group that was uncollapsed");
				//}					
			}
			// get a network view and apply a layout
			// TODO: move layout code to a utility method
			CyNetworkView networkView = getCurrentNetworkView();
			CyLayoutAlgorithm alg = getService(CyLayoutAlgorithmManager.class).getLayout("grid");
			Object context = alg.createLayoutContext();			
			TunableSetter setter = getService(TunableSetter.class);
			Map<String, Object> layoutArgs = new HashMap<>();
			layoutArgs.put("nodeVerticalSpacing", 80.0);
			layoutArgs.put("nodeHorizontalSpacing", 80.0);
			setter.applyTunables(context, layoutArgs);
			// layout the group nodes only
			if (networkView != null && networkView.getModel().equals(network)) {
				Set<View<CyNode>> nodeViews = new HashSet<>();
				for (CyNode node : group.getNodeList()) {
					nodeViews.add(networkView.getNodeView(node));
				}
				executeTask(alg.createTaskIterator(networkView, context, nodeViews, SharedProperties.SCORE));
			}
		}
	}

	
	public void aggregateGroupEdgeAttributes(CyNetwork retrievedNetwork, CyGroup group, CyEdge newEdge, List<CyNode> groupNodes, CyNode neighbor, List<String> edgeColsToAggregate) {
		CyNetwork rootNetwork = group.getRootNetwork(); 
		CyGroupManager groupManager = getService(CyGroupManager.class);
		// System.out.println(retrievedNetwork.getRow(neighbor).get(CyNetwork.NAME, String.class));
		// find out which edges we need to average
		List<CyEdge> edgesToAggregate = new ArrayList<CyEdge>();
		int numPossibleEdges = groupNodes.size();
		if (groupManager.isGroup(neighbor, retrievedNetwork)) {
			// if the neighbor is a group node, get the edges between nodes of both groups
			//System.out.println("found a neighbor that is a group as well: " + neighbor);
			CySubNetwork groupSubNet = (CySubNetwork)neighbor.getNetworkPointer();
			numPossibleEdges *= groupSubNet.getNodeCount();
			for (CyNode group1Node : groupSubNet.getNodeList()) {
				for (CyNode group2Node : groupNodes) {
					edgesToAggregate.addAll(rootNetwork.getConnectingEdgeList(group1Node, group2Node, Type.ANY));
				}
			}
			//System.out.println("edges to average: " + edgesToAverage.size());
		} else {
			//System.out.println("found a normal neighbor: " + neighbor);
			for (CyNode groupNode : groupNodes) {
				edgesToAggregate.addAll(rootNetwork.getConnectingEdgeList(groupNode, neighbor, Type.ANY));
			}
		}
		retrievedNetwork.getRow(newEdge).set(SharedProperties.EDGEPOSSIBLE, Integer.valueOf(numPossibleEdges));
		retrievedNetwork.getRow(newEdge).set(SharedProperties.EDGEEXISTING, Integer.valueOf(edgesToAggregate.size()));
		// retrievedNetwork.getRow(newEdge).set(SharedProperties.EDGEPROB, Double.valueOf((double)edgesToAggregate.size()/numPossibleEdges));
		// now get the average and set it for each column
		for (String col : edgeColsToAggregate) {
			double averagedValue = 0.0;
			for (CyEdge edge : edgesToAggregate) {
				Double edgeValue = rootNetwork.getRow(edge).get(col, Double.class);
				if (edgeValue != null) 
					averagedValue += edgeValue.doubleValue();
			}
			if (averagedValue != 0.0) {
				// TODO: shorten to 6 digits precision or not?
				// for edge attributes, we sum the values from all existing edges and divide by the number of possible edges
				retrievedNetwork.getRow(newEdge).set(col, Double.valueOf(averagedValue/numPossibleEdges));							
			}
		}
		// TODO: Hide this attribute if possible, because we need it but not the user 
		retrievedNetwork.getRow(newEdge).set(SharedProperties.EDGEAGGREGATED, Boolean.valueOf(true));
	}

	@Override
	public void handleEvent(SelectedNodesAndEdgesEvent event) {
		// TODO: figure out if and what to do when group nodes are selected
		// needs to add the listener to app manager in CyActivator
//		Collection<CyNode> nodes = event.getSelectedNodes();
//		CyNetwork network = event.getNetwork();
//		CyGroupManager groupManager = getService(CyGroupManager.class);
//		for (CyNode node : nodes) {
//			if (groupManager.isGroup(node, network)) {
//				CyGroup group = groupManager.getGroup(node, network);
//				for (CyNode groupNode : group.getNodeList()) {
//					if (network.getRow(groupNode) != null)
//						network.getRow(groupNode).set(CyNetwork.SELECTED, true);
//				}
//			}
//		}
	}
	

	@Override
	public void handleEvent(GroupAboutToCollapseEvent e) {
		// Auto-generated method stub
		//System.out.println("group about to collapse "
		//		+ e.getNetwork().getRow(e.getSource().getGroupNode()).get(CyNetwork.NAME, String.class));
	}


	@Override
	public void handleEvent(GroupEdgesAddedEvent e) {
		// Auto-generated method stub
		// System.out.println("added edges + " + e.getEdges());
	}

	public int getDefaultConfidence() {
		return SharedProperties.defaultConfidence;
	}
	
	public NetworkType getDefaultNetworkType() {
		return NetworkType.FUNCTIONAL;
	}

	public StringSpecies getDefaultSpecies() {
		// Currently, we select Human as default
		// TODO: change default species from human to user-defined once we have settings
		return StringSpecies.getHumanSpecies();
	}
}
