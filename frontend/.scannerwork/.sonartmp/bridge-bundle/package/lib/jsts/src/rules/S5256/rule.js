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
// https://sonarsource.github.io/rspec/#/rspec/S5256/javascript
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.rule = void 0;
const getElementType_1 = __importDefault(require("eslint-plugin-jsx-a11y/lib/util/getElementType"));
const jsx_ast_utils_1 = require("jsx-ast-utils");
const table_1 = require("../helpers/table");
const helpers_1 = require("../helpers");
exports.rule = {
    meta: {},
    create(context) {
        const checkValidTable = (tree) => {
            const grid = (0, table_1.computeGrid)(context, tree);
            if (grid === null) {
                // Unknown table structure, dont raise issue
                return true;
            }
            if (grid.length === 0) {
                return false;
            }
            for (const row of grid) {
                if (row.every(({ isHeader }) => isHeader)) {
                    return true;
                }
            }
            for (let col = 0; col < grid[0].length; col++) {
                if (grid.every(row => col >= row.length || row[col].isHeader)) {
                    return true;
                }
            }
            return false;
        };
        return {
            JSXElement(node) {
                const tree = node;
                const elementType = (0, getElementType_1.default)(context)(tree.openingElement);
                if (elementType === 'table') {
                    if ((0, helpers_1.isPresentationTable)(context, tree.openingElement)) {
                        return;
                    }
                    const ariaHidden = (0, jsx_ast_utils_1.getProp)(tree.openingElement.attributes, 'aria-hidden');
                    if (ariaHidden && (0, jsx_ast_utils_1.getLiteralPropValue)(ariaHidden) === true) {
                        return;
                    }
                    if (!checkValidTable(tree)) {
                        context.report({
                            node,
                            message: 'Add a valid header row or column to this "<table>".',
                        });
                    }
                }
            },
        };
    },
};
//# sourceMappingURL=rule.js.map