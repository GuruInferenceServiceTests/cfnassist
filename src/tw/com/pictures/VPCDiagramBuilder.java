package tw.com.pictures;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;
import tw.com.unit.SecurityChildDiagram;

import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.NetworkAcl;
import com.amazonaws.services.ec2.model.NetworkAclEntry;
import com.amazonaws.services.ec2.model.PortRange;
import com.amazonaws.services.ec2.model.Route;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.rds.model.DBInstance;

public class VPCDiagramBuilder {

	private static final String CIDR_ANY = "any";
	private Diagram networkDiagram;
	private Diagram securityDiagram;

	private Vpc vpc;
	private Map<String, SubnetDiagramBuilder> subnetDiagramBuilders; // id -> diagram

	public VPCDiagramBuilder(Vpc vpc, Diagram networkDiagram, Diagram securityDiagram) {
		this.vpc = vpc;
		this.networkDiagram = networkDiagram;
		this.securityDiagram = securityDiagram;
		subnetDiagramBuilders = new HashMap<String, SubnetDiagramBuilder>();
	}

	private void addTitle(Vpc vpc, Diagram diagram) {
		String title = vpc.getVpcId();
		String name = AmazonVPCFacade.getNameFromTags(vpc.getTags());
		if (!name.isEmpty()) {
			title = title + String.format(" (%s)", name);
		}
		diagram.addTitle(title);
	}

	public void add(String unique, SubnetDiagramBuilder subnetDiagram) {
		subnetDiagramBuilders.put(unique, subnetDiagram);
	}

	public void render(Recorder recorder) throws IOException {
		renderDiagramWithPrefix(recorder, networkDiagram, "network_diagram");
		renderDiagramWithPrefix(recorder, securityDiagram, "security_diagram");
	}

	private void renderDiagramWithPrefix(Recorder recorder, Diagram diagram,
			String prefix) throws IOException {
		addTitle(vpc, diagram);
		recorder.beginFor(vpc, prefix);	
		diagram.render(recorder);
		recorder.end();
	}

	public NetworkChildDiagram createNetworkDiagramForSubnet(Subnet subnet) throws CfnAssistException {
		String subnetName = AmazonVPCFacade.getNameFromTags(subnet.getTags());
		String label = SubnetDiagramBuilder.formSubnetLabel(subnet, subnetName);
		return new NetworkChildDiagram(networkDiagram.createSubDiagram(subnet.getSubnetId(), label));
	}
	
	public SecurityChildDiagram createSecurityDiagramForSubnet(Subnet subnet) throws CfnAssistException {
		String subnetName = AmazonVPCFacade.getNameFromTags(subnet.getTags());
		String label = SubnetDiagramBuilder.formSubnetLabel(subnet, subnetName);
		return new SecurityChildDiagram(securityDiagram.createSubDiagram(subnet.getSubnetId(), label));
	}

	public void addRouteTable(RouteTable routeTable, String subnetId) throws CfnAssistException {
		if (subnetId!=null) {
			SubnetDiagramBuilder subnetDiagram = subnetDiagramBuilders.get(subnetId);
			subnetDiagram.addRouteTable(routeTable);
		} else {
			String name = AmazonVPCFacade.getNameFromTags(routeTable.getTags());
			String routeTableId = routeTable.getRouteTableId();

			String label = AmazonVPCFacade.createLabelFromNameAndID(routeTableId, name);
			networkDiagram.addRouteTable(routeTableId, label);
		}
	}

	public void addEIP(Address eip) throws CfnAssistException {
		String label = AmazonVPCFacade.createLabelFromNameAndID(eip.getAllocationId() ,eip.getPublicIp());
		networkDiagram.addPublicIPAddress(eip.getPublicIp(), label);	
	}

	public void linkEIPToInstance(String publicIp, String instanceId) {
		networkDiagram.addConnectionBetween(publicIp, instanceId);			
	}

	public void addELB(LoadBalancerDescription elb) throws CfnAssistException {
		String label = elb.getLoadBalancerName();
		String id = elb.getDNSName();
		networkDiagram.addLoadBalancer(id, label);
	}

	public void associateELBToInstance(LoadBalancerDescription elb,
			String instanceId) {
		networkDiagram.addConnectionBetween(elb.getDNSName(), instanceId);
	}

	public void associateELBToSubnet(LoadBalancerDescription elb,
			String subnetId) {
		networkDiagram.associateWithSubDiagram(elb.getDNSName(), subnetId, subnetDiagramBuilders.get(subnetId));		
	}

	public void addDBInstance(DBInstance rds) throws CfnAssistException {
		String rdsId = rds.getDBInstanceIdentifier();
		String label = AmazonVPCFacade.createLabelFromNameAndID(rdsId, rds.getDBName());
		networkDiagram.addDBInstance(rdsId, label);
	}
	
	public void addAcl(NetworkAcl acl) throws CfnAssistException {
		String aclId = acl.getNetworkAclId();
		String name = AmazonVPCFacade.getNameFromTags(acl.getTags());
		String label = AmazonVPCFacade.createLabelFromNameAndID(aclId,name);
		securityDiagram.addACL(aclId, label);
	}

	// this is relying on the ID being the same on both diagrams (network & security)
	public void associateDBWithSubnet(DBInstance rds, String subnetId) {
		networkDiagram.associateWithSubDiagram(rds.getDBInstanceIdentifier(), subnetId, subnetDiagramBuilders.get(subnetId));	
	}

	// this is relying on the ID being the same on both diagrams (network & security)
	public void addRoute(String subnetId, Route route) {
		String destination = getDestination(route);
		String cidr = route.getDestinationCidrBlock();
		if (cidr==null) {
			cidr = "no cidr";
		}
		if (!destination.equals("local")) {
			networkDiagram.addConnectionFromSubDiagram(destination, subnetId, subnetDiagramBuilders.get(subnetId), cidr);
		} else {
			networkDiagram.associateWithSubDiagram(cidr, subnetId, subnetDiagramBuilders.get(subnetId));
		}		
	}
		
	private String getDestination(Route route) {
		String dest = route.getGatewayId();
		if (dest==null) {
			dest = route.getInstanceId();
		}
		return dest;
	}

	// this is relying on the ID being the same on both diagrams (network & security)
	public void associateAclWithSubnet(NetworkAcl acl, String subnetId) {
		securityDiagram.associateWithSubDiagram(acl.getNetworkAclId(), subnetId, subnetDiagramBuilders.get(subnetId));	
	}

	public void addOutboundRoute(String aclId, NetworkAclEntry outboundEntry, String subnetId) throws CfnAssistException {
		// item for address range
		String cidrBlock = outboundEntry.getCidrBlock();
		if (cidrBlock.equals("0.0.0.0/0")) {
			cidrBlock=CIDR_ANY;
		} else {
			securityDiagram.addCidr(cidrBlock);
		}
		// item for port range
		String uniqueId = String.format("%s_%d_%s", aclId, outboundEntry.getRuleNumber(), cidrBlock);
		PortRange portRange = outboundEntry.getPortRange();
		if (portRange==null) {
			securityDiagram.addPortRange(uniqueId, 0, 0); 
		} else {
			securityDiagram.addPortRange(uniqueId, portRange.getFrom(), portRange.getTo());
		}
		
		//  associate subnet with port range and port range with cidr
		securityDiagram.addConnectionFromSubDiagram(uniqueId, subnetId, subnetDiagramBuilders.get(subnetId), cidrBlock);
		if (!cidrBlock.equals(CIDR_ANY)) {
			securityDiagram.addConnectionBetween(uniqueId, cidrBlock);
		}
	}

}
