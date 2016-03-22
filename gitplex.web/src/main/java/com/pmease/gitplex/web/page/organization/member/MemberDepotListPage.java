package com.pmease.gitplex.web.page.organization.member;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.PageableListView;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import com.pmease.commons.wicket.behavior.OnTypingDoneBehavior;
import com.pmease.commons.wicket.component.DropdownLink;
import com.pmease.commons.wicket.component.clearable.ClearableTextField;
import com.pmease.gitplex.core.entity.Account;
import com.pmease.gitplex.core.entity.Depot;
import com.pmease.gitplex.core.security.privilege.DepotPrivilege;
import com.pmease.gitplex.web.Constants;
import com.pmease.gitplex.web.depotaccess.DepotAccess;
import com.pmease.gitplex.web.page.organization.PrivilegeSelectionPanel;

import de.agilecoders.wicket.core.markup.html.bootstrap.navigation.BootstrapPagingNavigator;
import de.agilecoders.wicket.core.markup.html.bootstrap.navigation.ajax.BootstrapAjaxPagingNavigator;

@SuppressWarnings("serial")
public class MemberDepotListPage extends MemberPage {

	private PageableListView<DepotPermission> depotsView;
	
	private BootstrapPagingNavigator pagingNavigator;
	
	private WebMarkupContainer depotsContainer; 
	
	private WebMarkupContainer noDepotsContainer;
	
	private DepotPrivilege filterPrivilege;
	
	public MemberDepotListPage(PageParameters params) {
		super(params);
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		TextField<String> searchField;
		
		add(searchField = new ClearableTextField<String>("searchDepots", Model.of("")));
		searchField.add(new OnTypingDoneBehavior(100) {

			@Override
			protected void onTypingDone(AjaxRequestTarget target) {
				target.add(depotsContainer);
				target.add(pagingNavigator);
				target.add(noDepotsContainer);
			}
			
		});
		
		WebMarkupContainer filterContainer = new WebMarkupContainer("filter");
		filterContainer.setOutputMarkupId(true);
		add(filterContainer);
		
		filterContainer.add(new DropdownLink("selection") {

			@Override
			protected void onInitialize() {
				super.onInitialize();
				add(new Label("label", new AbstractReadOnlyModel<String>() {

					@Override
					public String getObject() {
						if (filterPrivilege == null)
							return "Filter by privilege";
						else 
							return filterPrivilege.toString();
					}
					
				}));
			}

			@Override
			protected Component newContent(String id) {
				return new PrivilegeSelectionPanel(id) {

					@Override
					protected void onSelect(AjaxRequestTarget target, DepotPrivilege privilege) {
						close();
						filterPrivilege = privilege;
						target.add(filterContainer);
						target.add(depotsContainer);
						target.add(pagingNavigator);
						target.add(noDepotsContainer);
					}

				};
			}
		});
		filterContainer.add(new AjaxLink<Void>("clear") {

			@Override
			public void onClick(AjaxRequestTarget target) {
				filterPrivilege = null;
				target.add(filterContainer);
				target.add(depotsContainer);
				target.add(pagingNavigator);
				target.add(noDepotsContainer);
			}

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(filterPrivilege != null);
			}
			
		});
		
		depotsContainer = new WebMarkupContainer("depots") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(!depotsView.getModelObject().isEmpty());
			}
			
		};
		depotsContainer.setOutputMarkupPlaceholderTag(true);
		add(depotsContainer);
		
		depotsContainer.add(depotsView = new PageableListView<DepotPermission>("depots", 
				new LoadableDetachableModel<List<DepotPermission>>() {

			@Override
			protected List<DepotPermission> load() {
				List<DepotPermission> permissions = new ArrayList<>();
				
				String searchInput = searchField.getInput();
				if (searchInput != null)
					searchInput = searchInput.toLowerCase().trim();
				else
					searchInput = "";

				Account user = getMembership().getUser();
				for (Depot depot: getAccount().getDepots()) {
					if (depot.getName().toLowerCase().contains(searchInput)) {
						DepotPrivilege privilege = new DepotAccess(user, depot).getGreatestPrivilege();
						if (privilege != DepotPrivilege.NONE 
								&& (filterPrivilege == null || filterPrivilege == privilege)) {
							permissions.add(new DepotPermission(depot, privilege));
						}
					}
				}
				
				Collections.sort(permissions, new Comparator<DepotPermission>() {

					@Override
					public int compare(DepotPermission permission1, DepotPermission permission2) {
						return permission1.getDepot().getName().compareTo(permission2.getDepot().getName());
					}
					
				});
				return permissions;
			}
			
		}, Constants.DEFAULT_PAGE_SIZE) {

			@Override
			protected void populateItem(ListItem<DepotPermission> item) {
				DepotPermission permission = item.getModelObject();

				BookmarkablePageLink<Void> depotLink = new BookmarkablePageLink<Void>(
						"depotLink", 
						MemberPrivilegeSourcePage.class, 
						MemberPrivilegeSourcePage.paramsOf(getMembership(), permission.getDepot()));
				depotLink.add(new Label("name", permission.getDepot().getName()));
				item.add(depotLink);

				item.add(new Label("privilege", permission.getPrivilege().toString()));
			}
			
		});

		add(pagingNavigator = new BootstrapAjaxPagingNavigator("pageNav", depotsView) {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(depotsView.getPageCount() > 1);
			}
			
		});
		pagingNavigator.setOutputMarkupPlaceholderTag(true);
		
		noDepotsContainer = new WebMarkupContainer("noDepots") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(depotsView.getModelObject().isEmpty());
			}
			
		};
		noDepotsContainer.setOutputMarkupPlaceholderTag(true);
		add(noDepotsContainer);
	}

	private static class DepotPermission {
		
		private final Depot depot;
		
		private final DepotPrivilege privilege;
		
		public DepotPermission(Depot depot, DepotPrivilege privilege) {
			this.depot = depot;
			this.privilege = privilege;
		}

		public Depot getDepot() {
			return depot;
		}

		public DepotPrivilege getPrivilege() {
			return privilege;
		}
		
	}

}
