/*******************************************************************************
 * Copyright (c) 2018 Daniel Murygin.
 *
 * This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/
package sernet.verinice.service.commands.bp;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import sernet.hui.common.connect.IIdentifiableElement;
import sernet.verinice.interfaces.ChangeLoggingCommand;
import sernet.verinice.interfaces.CommandException;
import sernet.verinice.interfaces.IBaseDao;
import sernet.verinice.model.bp.elements.BpRequirement;
import sernet.verinice.model.bp.elements.Safeguard;
import sernet.verinice.model.bp.groups.BpRequirementGroup;
import sernet.verinice.model.bp.groups.SafeguardGroup;
import sernet.verinice.model.common.ChangeLogEntry;
import sernet.verinice.model.common.CnALink;
import sernet.verinice.model.common.CnATreeElement;
import sernet.verinice.model.common.Link;
import sernet.verinice.service.commands.CreateElement;
import sernet.verinice.service.commands.CreateMultipleLinks;

/**
 * Creates a dummy safeguard for a requirement if no safeguard is linked to
 * requirement.
 */
public class ModelDummySafeguards extends ChangeLoggingCommand {

    private static final long serialVersionUID = -5967763349544568712L;

    private static final Logger LOG = Logger.getLogger(ModelDummySafeguards.class);

    private Set<String> moduleUuidsFromScope;
    private Set<String> safeguardGroupUuidsFromScope;
    private List<CnATreeElement> safeguardGroups;

    private transient List<Link> linkList;

    private transient ModelingMetaDao metaDao;
    private String stationId;

    public ModelDummySafeguards(Set<String> moduleUuidsFromScope,
            Set<String> safeguardGroupUuidsFromScope) {
        super();
        this.moduleUuidsFromScope = moduleUuidsFromScope;
        this.safeguardGroupUuidsFromScope = safeguardGroupUuidsFromScope;
        this.stationId = ChangeLogEntry.STATION_ID;
    }

    @Override
    public void execute() {
        for (String uuid : moduleUuidsFromScope) {
            try {
                handleModule(uuid);
            } catch (Exception e) {
                LOG.error("Error while handling module with UUID: " + uuid, e); //$NON-NLS-1$
            }
        }
    }

    private void handleModule(String uuid) throws CommandException {
        Set<CnATreeElement> requirements = findSafeguardsByModuleUuid(uuid);
        linkList = new LinkedList<>();
        for (CnATreeElement requirement : requirements) {
            handleRequirement(requirement);
        }
        if (!linkList.isEmpty()) {
            CreateMultipleLinks createMultipleLinks = new CreateMultipleLinks(linkList);
            getCommandService().executeCommand(createMultipleLinks);
        }
    }

    private void handleRequirement(CnATreeElement requirement) throws CommandException {
        Set<CnALink> linksToSafeguard = getLinksToSafeguards(requirement);
        if (linksToSafeguard == null || linksToSafeguard.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No safeguards found for requirement with UUID: " //$NON-NLS-1$
                        + requirement.getUuid()
                        + ", will create a dummy safewguard now..."); //$NON-NLS-1$
            }
            Safeguard safeguard = createDummySafeguardForRequirement((BpRequirement) requirement);
            linkList.add(new Link(requirement, safeguard,
                    BpRequirement.REL_BP_REQUIREMENT_BP_SAFEGUARD));
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("Safeguards found for requirement with UUID: " + requirement.getUuid()); //$NON-NLS-1$
                for (CnALink link : linksToSafeguard) {
                    LOG.debug("Link type: " + link.getRelationId()); //$NON-NLS-1$
                }
        }
    }

    private Set<CnALink> getLinksToSafeguards(CnATreeElement requirement) {
        Set<CnALink> linksToSafeguard = requirement.getLinksDown();
        if (linksToSafeguard != null && !linksToSafeguard.isEmpty()) {
            linksToSafeguard = filterLinks(linksToSafeguard);
        }
        return linksToSafeguard;
    }

    private Set<CnALink> filterLinks(Set<CnALink> links) {
        Set<CnALink> linksToSafeguard = new HashSet<>(links.size());
        for (CnALink link : links) {
            if (BpRequirement.REL_BP_REQUIREMENT_BP_SAFEGUARD.equals(link.getRelationId())) {
                linksToSafeguard.add(link);
            }
        }
        return linksToSafeguard;
    }

    private Safeguard createDummySafeguardForRequirement(BpRequirement requirement)
            throws CommandException {
        CnATreeElement element = loadTargetElement(requirement);
        BpRequirementGroup requirementGroup = (BpRequirementGroup) requirement.getParent();
        String moduleTitle = requirementGroup.getFullTitle();
        CnATreeElement safeguardGroup = getSafeguardGroup(element, moduleTitle);
        if (safeguardGroup == null) {
            safeguardGroup = createSafeguardGroup(element, requirementGroup);
        }
        return createSafeguard(requirement, safeguardGroup);
    }

    private Safeguard createSafeguard(BpRequirement requirement, CnATreeElement safeguardGroup)
            throws CommandException {
        CreateElement<Safeguard> createElement = new CreateElement<>(safeguardGroup,
                Safeguard.class, requirement.getTitle() + " [" + Messages.getString("ModelDummySafeguards.6") + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        createElement = getCommandService().executeCommand(createElement);
        Safeguard safeguard = createElement.getNewElement();
        safeguard.setIdentifier(getSafeguardForRequirementIdentifier(requirement.getIdentifier()));
        safeguard.setSecurityLevel(requirement.getSecurityLevel());
        safeguard = (Safeguard) getMetaDao().save(safeguard);
        return safeguard;
    }

    private CnATreeElement createSafeguardGroup(CnATreeElement parent,
            BpRequirementGroup requirementGroup)
            throws CommandException {
        CreateElement<SafeguardGroup> createElement = new CreateElement<>(parent,
                SafeguardGroup.class, requirementGroup.getTitle());
        createElement = getCommandService().executeCommand(createElement);
        SafeguardGroup safeguardGroup = createElement.getNewElement();
        safeguardGroup.setIdentifier(requirementGroup.getIdentifier());
        return getMetaDao().save(safeguardGroup);
    }

    private CnATreeElement getSafeguardGroup(CnATreeElement element, String fullTitle) {
        CnATreeElement safeguardGroup = null;
        Set<CnATreeElement> children = element.getChildren();
        safeguardGroup = getSafeguardGroup(fullTitle, children);
        if (safeguardGroup == null && !safeguardGroupUuidsFromScope.isEmpty()) {
            if (safeguardGroups == null) {
                safeguardGroups = getMetaDao()
                        .loadElementsWithProperties(safeguardGroupUuidsFromScope);
            }
            safeguardGroup = getSafeguardGroup(fullTitle, safeguardGroups);
        }
        return safeguardGroup;
    }

    private CnATreeElement getSafeguardGroup(String fullTitle,
            Collection<CnATreeElement> allSafeguardGroups) {
        CnATreeElement safeguardGroup = null;
        for (CnATreeElement group : allSafeguardGroups) {
            if (SafeguardGroup.TYPE_ID.equals(group.getTypeId())
                    && fullTitle.equals(((IIdentifiableElement) group).getFullTitle())) {
                safeguardGroup = group;
            }
        }
        return safeguardGroup;
    }

    private CnATreeElement loadTargetElement(CnATreeElement requirement) {
        // Find target element
        CnATreeElement element = null;
        Set<CnALink> links = requirement.getLinksDown();
        Collection<String> linkTypes = ModelLinksCommand.ELEMENT_TO_REQUIREMENT_LINK_TYPE_IDS
                .values();
        for (CnALink link : links) {
            if (linkTypes.contains(link.getRelationId())) {
                element = link.getDependency();
            }
        }
        if (element == null) {
            return null;
        }
        return getMetaDao()
                .loadElementsWithChildrenProperties(Collections.singletonList(element.getUuid()))
                .get(0);
    }

    private String getSafeguardForRequirementIdentifier(String identifier) {
        return identifier.replace(".A", ".M"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public String getStationId() {
        return stationId;
    }

    @Override
    public int getChangeType() {
        return ChangeLogEntry.TYPE_INSERT;
    }

    private Set<CnATreeElement> findSafeguardsByModuleUuid(String uuid) {
        return getMetaDao().loadLinkedElementsOfParents(Arrays.asList(uuid));
    }

    public ModelingMetaDao getMetaDao() {
        if (metaDao == null) {
            metaDao = new ModelingMetaDao(getDao());
        }
        return metaDao;
    }

    private IBaseDao<CnATreeElement, Serializable> getDao() {
        return getDaoFactory().getDAO(CnATreeElement.class);
    }
}
