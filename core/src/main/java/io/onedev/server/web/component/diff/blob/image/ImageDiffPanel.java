package io.onedev.server.web.component.diff.blob.image;

import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.request.resource.DynamicImageResource;
import org.apache.wicket.request.resource.PackageResourceReference;

import io.onedev.commons.utils.Provider;
import io.onedev.server.git.Blob;
import io.onedev.server.git.BlobChange;
import io.onedev.server.git.BlobIdent;
import io.onedev.server.git.GitUtils;

@SuppressWarnings("serial")
public class ImageDiffPanel extends Panel {

	private final BlobChange change;
	
	public ImageDiffPanel(String id, BlobChange change) {
		super(id);
	
		this.change = change;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		add(newImage("old", change.getOldBlobIdent(), new Provider<Blob>() {

			@Override
			public Blob get() {
				return change.getOldBlob();
			}
			
		}));
		
		add(newImage("new", change.getNewBlobIdent(), new Provider<Blob>() {

			@Override
			public Blob get() {
				return change.getNewBlob();
			}
			
		}));
		
	}
	
	private Image newImage(String id, BlobIdent blobIdent, Provider<Blob> blobProvider) {
		Image image;
		if (blobIdent.path != null) {
			add(image = new Image(id, new DynamicImageResource() {
				
				@Override
				protected void configureResponse(ResourceResponse response, Attributes attributes) {
					super.configureResponse(response, attributes);
					if (!GitUtils.isHash(blobIdent.revision))
						response.disableCaching();
					response.setContentType(blobProvider.get().getMediaType().toString());
					response.setFileName(blobIdent.getName());
				}

				@Override
				protected byte[] getImageData(Attributes attributes) {
					return blobProvider.get().getBytes();
				}
			}));
		} else {
			add(image = new Image(id, new PackageResourceReference(ImageDiffPanel.class, "blank.png")));
		}
		return image;
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.render(JavaScriptHeaderItem.forReference(new ImageDiffResourceReference()));
		String script = String.format("onedev.server.imageDiff.onDomReady('%s');", getMarkupId());
		response.render(OnDomReadyHeaderItem.forScript(script));
	}

}
