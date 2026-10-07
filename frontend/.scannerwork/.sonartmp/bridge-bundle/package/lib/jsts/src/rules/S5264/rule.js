"use strict";
/*
 * SonarQube JavaScript Plugin
 * Copyright (C) 2011-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
// https://sonarsource.github.io/rspec/#/rspec/S5264/javascript
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.rule = void 0;
const isHiddenFromScreenReader_1 = __importDefault(require("eslint-plugin-jsx-a11y/lib/util/isHiddenFromScreenReader"));
const getElementType_1 = __importDefault(require("eslint-plugin-jsx-a11y/lib/util/getElementType"));
exports.rule = {
    meta: {
        messages: {
            addContent: 'Add an accessible content to this "<object>" tag.',
        },
    },
    create(context) {
        const elementType = (0, getElementType_1.default)(context);
        function hasVisibleChildren(children) {
            return children.some((child) => {
                switch (child.type) {
                    case 'JSXText':
                        return !!child.value.trim();
                    case 'JSXFragment':
                        return hasVisibleChildren(child.children);
                    case 'JSXElement':
                        return !(0, isHiddenFromScreenReader_1.default)(elementType(child.openingElement), child.openingElement.attributes);
                    case 'JSXExpressionContainer':
                        if (child.expression.type === 'Identifier') {
                            return child.expression.name !== 'undefined';
                        }
                        return child.expression.type !== 'JSXEmptyExpression';
                    default:
                        return false;
                }
            });
        }
        return {
            JSXElement(node) {
                const jsxNode = node;
                const type = elementType(jsxNode.openingElement);
                if (type.toLowerCase() !== 'object') {
                    return;
                }
                if (!hasVisibleChildren(jsxNode.children)) {
                    context.report({
                        node,
                        messageId: 'addContent',
                    });
                }
            },
        };
    },
};
//# sourceMappingURL=rule.js.map