package tw.com.pictures;

import java.io.IOException;
import java.util.List;

import javax.management.InvalidApplicationException;

import tw.com.exceptions.CfnAssistException;
import tw.com.pictures.dot.Recorder;
import tw.com.providers.RDSClient;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;

import com.amazonaws.services.ec2.model.Vpc;

public class DiagramCreator {
	
	private CloudRepository cloudRepository;
	private ELBRepository elbRepository;
	private RDSClient rdsClient;
	
	public DiagramCreator(RDSClient rdsClient, CloudRepository cloudClient, ELBRepository elbRepository) {
		this.rdsClient = rdsClient;
		this.cloudRepository = cloudClient;
		this.elbRepository = elbRepository;
	}

	public void createDiagrams(Recorder recorder) throws IOException, CfnAssistException, InvalidApplicationException {
		
		AmazonVPCFacade facade = new AmazonVPCFacade(cloudRepository, elbRepository, rdsClient);
		
		List<Vpc> vpcs = facade.getVpcs();
				
		DiagramBuilder diagrams = new DiagramBuilder();
		DiagramFactory diagramFactory = new DiagramFactory();
		VPCVisitor visitor = new VPCVisitor(diagrams, facade, diagramFactory);
		for(Vpc vpc : vpcs) {
			visitor.visit(vpc);
		}
		diagrams.render(recorder);
	}


}
