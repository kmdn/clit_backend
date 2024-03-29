package clit;

import java.util.Collection;

import experiment.EnumComponentType;
import experiment.PipelineItem;
import structure.datatypes.AnnotatedDocument;
import structure.interfaces.pipeline.PipelineComponent;

public class APIComponent implements PipelineComponent {

	private final String urlString;
	private final String displayName;
	private Collection<EnumComponentType> availableTasks;

	public APIComponent(final String urlString, final String displayName, final Collection<EnumComponentType> types) {
		this.urlString = urlString;
		this.displayName = displayName;
		this.availableTasks = types;
	}

	public String getUrlString() {
		return urlString;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Collection<EnumComponentType> getTasks() {
		return this.availableTasks;
	}

	@Override
	public Collection<AnnotatedDocument> execute(PipelineItem callItem, AnnotatedDocument document) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
