package tw.com.unit;

import javax.management.InvalidApplicationException;

import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;

import tw.com.VpcTestBuilder;
import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.NetworkChildDiagram;
import tw.com.pictures.SubnetDiagramBuilder;
import tw.com.pictures.VPCDiagramBuilder;

@RunWith(EasyMockRunner.class)
public class TestSubnetDiagramBuilder extends EasyMockSupport {

	private NetworkChildDiagram networkDiagram;
	private SecurityChildDiagram securityDiagram;
	private SubnetDiagramBuilder subnetDiagramBuilder;
	
	@Before
	public void beforeEachTestRuns() {
		networkDiagram = createStrictMock(NetworkChildDiagram.class);
		securityDiagram = createStrictMock(SecurityChildDiagram.class);
		createStrictMock(VPCDiagramBuilder.class);
		Subnet subnet = new Subnet().withSubnetId("subnetId").withCidrBlock("cidrBlock");
		subnetDiagramBuilder = new SubnetDiagramBuilder(networkDiagram, securityDiagram, subnet);
	}

	@Test
	public void shouldAddInstanceToDiagram() throws CfnAssistException, InvalidApplicationException {
		Instance instance = new Instance().
				withInstanceId("instacneId").
				withPrivateIpAddress("privateIp").
				withTags(new Tag().withKey("Name").withValue("instanceName"));

		networkDiagram.addInstance("instacneId", "instanceName\n[instacneId]\n(privateIp)");
		securityDiagram.addInstance("instacneId", "instanceName\n[instacneId]\n(privateIp)");
		
		replayAll();
		subnetDiagramBuilder.add(instance);
		verifyAll();
	}
	
	@Test
	public void shouldAddRouteTable() throws CfnAssistException {
		RouteTable routeTable = new RouteTable().
				withRouteTableId("routeTableId").
				withTags(new Tag().withKey("Name").withValue("routeTableName"));;

		networkDiagram.addRouteTable("routeTableId", "routeTableName [routeTableId]");
		
		replayAll();
		subnetDiagramBuilder.addRouteTable(routeTable);
		verifyAll();
	}
	
	@Test
	public void shouldAddSecurityGroupToDiagram() throws CfnAssistException, InvalidApplicationException {
		SecurityGroup group = new SecurityGroup().
				withGroupId("groupId").
				withGroupName("fullGroupName").
				withTags(VpcTestBuilder.CreateNameTag("name"));
		
		securityDiagram.addSecurityGroup("groupId","name [groupId]");
		
		replayAll();
		subnetDiagramBuilder.addSecurityGroup(group);
		verifyAll();	
	}

}
