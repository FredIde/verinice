/*******************************************************************************
 * Copyright (c) 2015 Daniel Murygin.
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
 * Contributors:
 *     Daniel Murygin <dm[at]sernet[dot]de> - initial API and implementation
 ******************************************************************************/
package sernet.verinice.service.linktable;

import sernet.verinice.service.linktable.antlr.VqlParserTokenTypes;

/**
 * @author Daniel Murygin <dm[at]sernet[dot]de>
 */
public final class PathElementFactory {

    private PathElementFactory() {
        super();
    }

    /**
     * @param delimiter
     */
    public static IPathElement<?,?> getElement(String delimiter) {
        switch (delimiter.toCharArray()[0]) {
        case IPathElement.DELIMITER_LINK:
            return new LinkElement();
        case IPathElement.DELIMITER_LINK_TYPE:
            return new LinkTypeElement();
        case IPathElement.DELIMITER_CHILD:
            return new ChildElement();
        case IPathElement.DELIMITER_PARENT:
            return new ParentElement();
        case IPathElement.DELIMITER_PROPERTY:
            return new ElementPropertyElement();
        default:
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <P, E, C> IPathElement<E,C> getElement(IPathElement<P, E> parent, int antlrTokenType) {
        switch (antlrTokenType) {
        case VqlParserTokenTypes.LINK:
            return (IPathElement<E, C>) new LinkElement();
        case VqlParserTokenTypes.LT:
            return (IPathElement<E, C>) new LinkTypeElement();
        case VqlParserTokenTypes.CHILD:
            return (IPathElement<E, C>) new ChildElement();
        case VqlParserTokenTypes.PARENT:
            return (IPathElement<E, C>) new ParentElement();
        case  VqlParserTokenTypes.PROP: {
            if(parent instanceof LinkTypeElement) {
                return (IPathElement<E, C>) new LinkPropertyElement();
            } else {
                return (IPathElement<E, C>) new ElementPropertyElement();
            }
        }
        default:
            return null;
        }
    }



}
