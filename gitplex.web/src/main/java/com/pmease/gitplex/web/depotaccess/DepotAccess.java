package com.pmease.gitplex.web.depotaccess;

import java.util.ArrayList;
import java.util.List;

import com.pmease.gitplex.core.entity.Account;
import com.pmease.gitplex.core.entity.Depot;
import com.pmease.gitplex.core.entity.OrganizationMembership;
import com.pmease.gitplex.core.entity.TeamAuthorization;
import com.pmease.gitplex.core.entity.TeamMembership;
import com.pmease.gitplex.core.entity.UserAuthorization;
import com.pmease.gitplex.core.security.privilege.DepotPrivilege;

public class DepotAccess {
	
	private final Account user;
	
	private final Depot depot;
	
	private transient List<PrivilegeSource> privilegeSources;
	
	private transient DepotPrivilege greatestPrivilege;
	
	public DepotAccess(Account user, Depot depot) {
		this.user = user;
		this.depot = depot;
	}

	public Account getUser() {
		return user;
	}

	public Depot getDepot() {
		return depot;
	}
	
	public List<PrivilegeSource> getPrivilegeSources() {
		if (privilegeSources == null) {
			privilegeSources = new ArrayList<>();
			if (depot.isPublicRead()) {
				privilegeSources.add(new IsPublicDepot());
			}
			if (user.isAdministrator()) {
				privilegeSources.add(new IsSystemAdministrator());
			}
			if (user.equals(depot.getAccount())) {
				privilegeSources.add(new IsDepotOwner());
			}
			
			OrganizationMembership membership = null;
			for (OrganizationMembership each: depot.getAccount().getOrganizationMembers()) {
				if (each.getUser().equals(user)) {
					membership = each;
					break;
				}
			}
			if (membership != null) {
				if (membership.isAdmin()) {
					privilegeSources.add(new IsOrganizationAdmin(membership));
				}
				if (membership.getOrganization().getDefaultPrivilege() != DepotPrivilege.NONE) {
					privilegeSources.add(new IsOrganizationMember(membership));
				}
			}
			for (TeamAuthorization authorization: depot.getAccount().getAllTeamAuthorizationsInOrganization()) {
				if (authorization.getDepot().equals(depot)) {
					for (TeamMembership teamMembership: depot.getAccount().getAllTeamMembershipsInOrganiation()) {
						if (teamMembership.getUser().equals(user) 
								&& teamMembership.getTeam().equals(authorization.getTeam())) {
							privilegeSources.add(new IsTeamMember(teamMembership, authorization.getPrivilege()));
						}
					}
				}
			}
			for (UserAuthorization authorization: depot.getAccount().getAllUserAuthorizationsInOrganization()) {
				if (authorization.getDepot().equals(depot) && authorization.getUser().equals(user)) {
					privilegeSources.add(new IsDepotCollaborator(authorization));
					break;
				}
			}
		}
		return privilegeSources;
	}
	
	public DepotPrivilege getGreatestPrivilege() {
		if (greatestPrivilege == null) {
			greatestPrivilege = DepotPrivilege.NONE;
			for (PrivilegeSource source: getPrivilegeSources()) {
				if (source.getPrivilege().can(greatestPrivilege))
					greatestPrivilege = source.getPrivilege();
			}
		}
		return greatestPrivilege;
	}
	
}
