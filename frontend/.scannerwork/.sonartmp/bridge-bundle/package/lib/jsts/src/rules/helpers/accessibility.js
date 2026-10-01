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
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.isPresentationTable = void 0;
const jsx_ast_utils_1 = require("jsx-ast-utils");
const getElementType_1 = __importDefault(require("eslint-plugin-jsx-a11y/lib/util/getElementType"));
function isPresentationTable(context, node) {
    const DISALLOWED_VALUES = ['presentation', 'none'];
    const type = (0, getElementType_1.default)(context)(node);
    if (type.toLowerCase() !== 'table') {
        return false;
    }
    const role = (0, jsx_ast_utils_1.getProp)(node.attributes, 'role');
    if (!role) {
        return false;
    }
    const roleValue = String((0, jsx_ast_utils_1.getLiteralPropValue)(role));
    return DISALLOWED_VALUES.includes(roleValue?.toLowerCase());
}
exports.isPresentationTable = isPresentationTable;
//# sourceMappingURL=accessibility.js.map