package io.onedev.server.web.editable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import org.apache.wicket.Component;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.event.Broadcast;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.feedback.FencedFeedbackPanel;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.convert.ConversionException;
import org.apache.wicket.util.visit.IVisit;
import org.apache.wicket.util.visit.IVisitor;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.IValidator;

import io.onedev.commons.launcher.loader.AppLoader;
import io.onedev.commons.utils.StringUtils;
import io.onedev.server.util.EditContext;
import io.onedev.server.util.OneContext;
import io.onedev.server.web.editable.PathElement.Named;
import io.onedev.server.web.editable.annotation.Horizontal;
import io.onedev.server.web.editable.annotation.OmitName;
import io.onedev.server.web.editable.annotation.Vertical;

@SuppressWarnings("serial")
public class BeanEditor extends ValueEditor<Serializable> {

	public static final String SCRIPT_CONTEXT_BEAN = "beanEditor";
	
	private final BeanDescriptor beanDescriptor;
	
	private final List<PropertyContext<Serializable>> propertyContexts = new ArrayList<>();
	
	private final boolean vertical;
	
	private RepeatingView propertiesView;
	
	public BeanEditor(String id, BeanDescriptor beanDescriptor, IModel<Serializable> model) {
		super(id, model);
		
		this.beanDescriptor = beanDescriptor;
		
		for (PropertyDescriptor propertyDescriptor: beanDescriptor.getPropertyDescriptors())
			propertyContexts.add(PropertyContext.of(propertyDescriptor));
		
		Class<?> beanClass = beanDescriptor.getBeanClass();
		if (beanClass.getAnnotation(Vertical.class) != null)
			vertical = true;
		else if (beanClass.getAnnotation(Horizontal.class) != null)
			vertical = false;
		else 
			vertical = true;
	}

	private boolean hasTransitiveDependency(String dependentPropertyName, String dependencyPropertyName, 
			Set<String> checkedPropertyNames) {
		if (checkedPropertyNames.contains(dependentPropertyName))
			return false;
		checkedPropertyNames.add(dependentPropertyName);
		Set<String> directDependencies = getPropertyContext(dependentPropertyName).getDescriptor().getDependencyPropertyNames();
		if (directDependencies.contains(dependencyPropertyName))
			return true;
		for (String directDependency: directDependencies) {
			if (hasTransitiveDependency(directDependency, dependencyPropertyName, new HashSet<>(checkedPropertyNames)))
				return true;
		}
		return false;
	}
	
	@Override
	public void onEvent(IEvent<?> event) {
		super.onEvent(event);
		
		if (event.getPayload() instanceof PropertyUpdating) {
			event.stop();
			PropertyUpdating propertyUpdating = (PropertyUpdating) event.getPayload();
			List<PropertyContainer> propertyContainers = new ArrayList<>();
			for (Component item: propertiesView)
				propertyContainers.add((PropertyContainer) item);
			for (PropertyContainer propertyContainer: propertyContainers) {
				int propertyIndex = (int) propertyContainer.getDefaultModelObject();
				PropertyContext<Serializable> propertyContext = propertyContexts.get(propertyIndex);
				Set<String> checkedPropertyNames = new HashSet<>();
				if (hasTransitiveDependency(propertyContext.getPropertyName(), 
						propertyUpdating.getPropertyName(), checkedPropertyNames)) {
					propertyUpdating.getHandler().add(propertyContainer);
					String script = String.format("$('#%s').addClass('no-autofocus');", propertyContainer.getMarkupId());
					propertyUpdating.getHandler().appendJavaScript(script);
				}
			}
			validate();
			if (!hasErrors(true)) 
				send(this, Broadcast.BUBBLE, new BeanUpdating(propertyUpdating.getHandler()));
			else
				clearErrors(true);
		}		
	}

	private WebMarkupContainer newItem(String id, int propertyIndex) {
		PropertyContext<Serializable> propertyContext = propertyContexts.get(propertyIndex);
		
		WebMarkupContainer item = new PropertyContainer(id, propertyIndex) {

			private Label descriptionLabel;
			
			@Override
			protected void onInitialize() {
				super.onInitialize();
				
				setOutputMarkupPlaceholderTag(true);
				
				WebMarkupContainer nameContainer;
				WebMarkupContainer valueContainer;
				if (!vertical) {
					add(nameContainer = new WebMarkupContainer("name"));
					add(valueContainer = new WebMarkupContainer("value"));
				} else {
					nameContainer = this;
					valueContainer = this;
				}
				Label nameLabel = new Label("name", propertyContext.getDescriptor().getDisplayName(this));
				nameContainer.add(nameLabel);
				
				OmitName omitName = propertyContext.getPropertyGetter().getAnnotation(OmitName.class);
				if (omitName != null && omitName.value() != OmitName.Place.VIEWER) {
					if (!vertical) {
						nameContainer.setVisible(false);
						valueContainer.add(AttributeAppender.replace("colspan", "2"));
					} else {
						nameLabel.setVisible(false);
					}
				}

				String required;
				if (propertyContext.getDescriptor().isPropertyRequired() 
						&& propertyContext.getPropertyClass() != boolean.class
						&& propertyContext.getPropertyClass() != Boolean.class) {
					required = "*";
				} else {
					required = "&nbsp;";
				}
				
				nameContainer.add(new Label("required", required).setEscapeModelStrings(false));

				Serializable propertyValue;		
				
				OneContext context = new OneContext(this);
				
				OneContext.push(context);
				try {
					propertyValue = (Serializable) propertyContext.getDescriptor().getPropertyValue(getModelObject());
				} finally {
					OneContext.pop();
				}
				PropertyEditor<Serializable> propertyEditor = propertyContext.renderForEdit("value", Model.of(propertyValue)); 
				valueContainer.add(propertyEditor);
				
				descriptionLabel = new Label("description", propertyContext.getDescriptor().getDescription(this)) {

					@Override
					protected void onConfigure() {
						super.onConfigure();
						setVisible(StringUtils.isNotBlank(getModelValue()));
					}
					
				};
				descriptionLabel.setEscapeModelStrings(false);
				descriptionLabel.setOutputMarkupPlaceholderTag(true);
				valueContainer.add(descriptionLabel);
				
				valueContainer.add(new FencedFeedbackPanel("feedback", propertyEditor));
				
				valueContainer.add(AttributeAppender.append("class", "property-" + propertyContext.getPropertyName()));
			}

			@Override
			public void onEvent(IEvent<?> event) {
				super.onEvent(event);
				if (event.getPayload() instanceof PropertyUpdating)
					((PropertyUpdating)event.getPayload()).getHandler().add(descriptionLabel);
			}

			@Override
			public Object getInputValue(String name) {
				/*
				 * Field will be display name of the property when the bean class being edited is 
				 * generated via groovy script    
				 */
				String propertyName = beanDescriptor.getPropertyName(name);
				propertyContext.getDescriptor().getDependencyPropertyNames().add(propertyName);
				
				Optional<Object> result= BeanEditor.this.visitChildren(PropertyEditor.class, new IVisitor<PropertyEditor<?>, Optional<Object>>() {

					@Override
					public void component(PropertyEditor<?> object, IVisit<Optional<Object>> visit) {
						if (object.getDescriptor().getPropertyName().equals(propertyName))
							visit.stop(Optional.ofNullable(object.getConvertedInput()));
						else  
							visit.dontGoDeeper();
					}
					
				});
				if (result == null)
					return getPropertyContext(propertyName).getDescriptor().getPropertyValue(getModelObject());
				else
					return result.orElse(null);
			}

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(!propertyContext.getDescriptor().isPropertyExcluded() 
						&& propertyContext.getDescriptor().isPropertyVisible(new OneContext(this), beanDescriptor));
			}

		};

		return item;
	}

	public PropertyContext<Serializable> getPropertyContext(String propertyName) {
		for (PropertyContext<Serializable> propertyContext: propertyContexts) {
			if (propertyContext.getPropertyName().equals(propertyName))
				return propertyContext;
		}
		throw new RuntimeException("Property not found: " + propertyName);
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();

		Fragment fragment;
		if (vertical) {
			fragment = new Fragment("content", "verticalFrag", this);
			fragment.add(AttributeAppender.append("class", " vertical"));
		} else {
			fragment = new Fragment("content", "horizontalFrag", this);
			fragment.add(AttributeAppender.append("class", " horizontal"));
		}
		
		add(fragment);
		
		propertiesView = new RepeatingView("properties");
		fragment.add(propertiesView);
		
		for (int i=0; i<propertyContexts.size(); i++) {
			propertiesView.add(newItem(propertiesView.newChildId(), i));
		}
		
		add(new IValidator<Serializable>() {

			@Override
			public void validate(IValidatable<Serializable> validatable) {
				OneContext.push(getOneContext());
				try {
					Validator validator = AppLoader.getInstance(Validator.class);
					for (ConstraintViolation<Serializable> violation: validator.validate(validatable.getValue())) {
						ErrorContext errorContext = getErrorContext(new ValuePath(violation.getPropertyPath()));
						if (errorContext != null)
							errorContext.addError(violation.getMessage());
					}
				} finally {
					OneContext.pop();
				}
			}
			
		});
		
		add(AttributeAppender.append("class", "bean-editor editable"));
		
		setOutputMarkupId(true);
	}
	
	public BeanDescriptor getBeanDescriptor() {
		return beanDescriptor;
	}

	public List<PropertyContext<Serializable>> getPropertyContexts() {
		return propertyContexts;
	}
	
	@Override
	public ErrorContext getErrorContext(PathElement element) {
		PathElement.Named namedElement = (Named) element;
		return visitChildren(PropertyEditor.class, new IVisitor<PropertyEditor<Serializable>, PropertyEditor<Serializable>>() {

			@Override
			public void component(PropertyEditor<Serializable> object, IVisit<PropertyEditor<Serializable>> visit) {
				if (object.getDescriptor().getPropertyName().equals(namedElement.getName()) && object.isVisibleInHierarchy())
					visit.stop(object);
				else
					visit.dontGoDeeper();
			}
			
		});
	}

	@Override
	protected Serializable convertInputToValue() throws ConversionException {
		final Serializable bean = (Serializable) getBeanDescriptor().newBeanInstance();
		
		visitChildren(PropertyEditor.class, new IVisitor<PropertyEditor<Serializable>, PropertyEditor<Serializable>>() {

			@Override
			public void component(PropertyEditor<Serializable> object, IVisit<PropertyEditor<Serializable>> visit) {
				if (!object.getDescriptor().isPropertyExcluded())
					object.getDescriptor().setPropertyValue(bean, object.getConvertedInput());
				visit.dontGoDeeper();
			}
			
		});
		
		return bean;
	}
	
	public OneContext getOneContext() {
		return new OneContext(this) {

			@Override
			public OneContext getPropertyContext(String propertyName) {
				for (Component item: propertiesView) {
					int propertyIndex = (int) item.getDefaultModelObject();
					PropertyContext<Serializable> propertyContext = propertyContexts.get(propertyIndex); 
					if (propertyContext.getPropertyName().equals(propertyName))
						return new OneContext(item);
				}
				return null;
			}
			
		};
	}
	
	private abstract class PropertyContainer extends WebMarkupContainer implements EditContext {

		public PropertyContainer(String id, int propertyIndex) {
			super(id, Model.of(propertyIndex));
		}

		@Override
		public void renderHead(IHeaderResponse response) {
			super.renderHead(response);
			
			response.render(JavaScriptHeaderItem.forReference(new PropertyContainerResourceReference()));
			
			String script = String.format("onedev.server.propertyContainer.onDomReady('%s');", getMarkupId());
			response.render(OnDomReadyHeaderItem.forScript(script));
		}

	}
	
}
