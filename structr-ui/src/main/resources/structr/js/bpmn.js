/*
 * Copyright (C) 2010-2019 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */

var bpmnMain, bpmnTree, bpmnContents, bpmnContext;
var drop;
var selectedElements = [];
var activeMethodId, methodContents = {};
var currentWorkingDir;
var methodPageSize = 10000, methodPage = 1;
var timeout, attempts = 0, maxRetry = 10;
var displayingFavorites = false;
var bpmnLastOpenMethodKey = 'structrBPMNLastOpenMethod_' + port;
var bpmnResizerLeftKey = 'structrBPMNResizerLeftKey_' + port;
var activeBPMNTabPrefix = 'activeBPMNTabPrefix' + port;

$(document).ready(function () {
	Structr.registerModule(_BPMN);
});

var _BPMN = {
	_moduleName: 'bpmn',
	pathLocationStack: [],
	pathLocationIndex: 0,
	searchThreshold: 3,
	searchTextLength: 0,
	lastClickedPath: '',
	bpmnRecentElementsKey: 'structrBPMNRecentElements_' + port,
	timeouts: {},
	init: function () {

		_Logger.log(_LogType.BPMN, '_BPMN.init');

		Structr.makePagesMenuDroppable();
		Structr.adaptUiToAvailableFeatures();

	},
	resize: function () {

		var windowHeight = $(window).height();
		var headerOffsetHeight = 100;

		if (bpmnTree) {
			bpmnTree.css({
				height: windowHeight - headerOffsetHeight - 27 + 'px'
			});
		}

		if (bpmnContents) {
			bpmnContents.css({
				height: windowHeight - headerOffsetHeight - 11 + 'px'
			});
		}

		if (bpmnContext) {
			bpmnContext.css({
				height: windowHeight - headerOffsetHeight - 11 + 'px'
			});
		}

		_BPMN.moveResizer();
		Structr.resize();
	},
	moveResizer: function (left) {
		left = left || LSWrapper.getItem(bpmnResizerLeftKey) || 300;
		$('.column-resizer', bpmnMain).css({left: left});

		var contextWidth = 240;
		var width = $(window).width() - left - contextWidth - 80;

		$('#bpmn-tree').css({width: left - 14 + 'px'});
		$('#bpmn-contents').css({left: left + 8 + 'px', width: width + 'px'});
		$('#bpmn-context').css({left: left + width + 41 + 'px', width: contextWidth + 'px'});
	},
	onload: function () {

		Structr.fetchHtmlTemplate('bpmn/main', {}, function (html) {

			main.append(html);

			_BPMN.init();

			bpmnMain = $('#bpmn-main');
			bpmnTree = $('#bpmn-tree');
			bpmnContents = $('#bpmn-contents');
			bpmnContext = $('#bpmn-context');

			_BPMN.moveResizer();
			Structr.initVerticalSlider($('.column-resizer', bpmnMain), bpmnResizerLeftKey, 204, _BPMN.moveResizer);

			$.jstree.defaults.core.themes.dots = false;
			$.jstree.defaults.dnd.inside_pos = 'last';
			$.jstree.defaults.dnd.large_drop_target = true;

			bpmnTree.on('select_node.jstree', _BPMN.handleTreeClick);
			bpmnTree.on('refresh.jstree', _BPMN.activateLastClicked);

			_TreeHelper.initTree(bpmnTree, _BPMN.treeInitFunction, 'structr-ui-bpmn');

			$(window).off('resize').resize(function () {
				_BPMN.resize();
			});

			Structr.unblockMenu(100);

			_BPMN.resize();
			Structr.adaptUiToAvailableFeatures();

			$('#tree-search-input').on('input', _BPMN.doSearch);
			$('#tree-forward-button').on('click', _BPMN.pathLocationForward);
			$('#tree-back-button').on('click', _BPMN.pathLocationBackward);
			$('#cancel-search-button').on('click', _BPMN.cancelSearch);

			/*
			$(window).on('keydown.search', function (e) {
				if (_BPMN.searchIsActive()) {
					if (e.key === 'Escape') {
						_BPMN.cancelSearch();
					}
				}
				;
			});
			*/

		});

	},
	treeInitFunction: function (obj, callback) {

		switch (obj.id) {

			case '#':

				var defaultDiagramEntries = [
					{
						id: 'running',
						text: 'Manage Running Processes',
						children: false,
						icon: _Icons.exec_icon,
						path: '/'
					},
					{
						id: 'root',
						text: '/',
						children: true,
						icon: _Icons.structr_logo_small,
						path: '/',
						state: {
							opened: true,
							selected: true
						}
					}
				];

				callback(defaultDiagramEntries);
				break;

			case 'root':
				callback([]);
				break;

			case 'running':
				callback([]);
				break;

			default:
				break;
		}

	},
	handleTreeClick: function(event, data) {

		switch (data.node.id) {

			case 'running':
				_BPMN.showManageProcesses();
				break;
		}
	},
	showManageProcesses: function() {

		Structr.fetchHtmlTemplate('bpmn/manage', {}, function (html) {

			$('#bpmn-contents').empty();
			$('#bpmn-contents').append(html);

			//query: function(type, pageSize, page, sort, order, properties, callback, exact, view, customView) {
			Command.query('SchemaNode', 1000, 1, 'name', 'asc', { extendsClass: 'org.structr.bpmn.model.BPMNProcess' }, function(result) {

				let table = document.querySelector('#bpmn-available-process-list');

				result.forEach(function(p) {

					Structr.fetchHtmlTemplate('bpmn/available-process-table-row', { process: p, status: p.status }, function (html) {

						$(table).append(html);

						$('#start-' + p.name).on('click', function() {

							let data = {
								type: p.name
							};

							Command.create(data, function(p) {

								$('#bpmn-running-process-list').prepend('<tr id="row-' + p.id + '"><td></td><td></td><td></td><td></td></tr>');
								_BPMN.enableContinuousUpdate(p.id);
							});
						});
					});
				});

			}, true, 'public');


			//query: function(type, pageSize, page, sort, order, properties, callback, exact, view, customView) {
			Command.query('BPMNProcess', 1000, 1, 'createdDate', 'desc', undefined, function(result) {

				let table = $('#bpmn-running-process-list');

				result.forEach(function(p) {

					_BPMN.appendRow(table, p);
				});

			}, true, 'public');


		});

	},
	updateRow: function(id) {

		Command.get(id, "id,type,status", function(p) {

			_BPMN.appendRow($('#row-' + id), p, true);
		});
	},
	appendRow: function(container, p, replace) {

		Structr.fetchHtmlTemplate('bpmn/running-process-table-row', { process: p, status: p.status }, function (html) {

			if (replace) {
				container.replaceWith(html);
			} else {
				container.append(html);
			}

			if (p && p.status && p.status.currentStep && !p.status.finished && p.status.suspended) {

				let stepId = p.status.currentStep.id;

				$('#actions-' + stepId).append('<button class="action button" id="finish-' + stepId + '">' + _Icons.getHtmlForIcon(_Icons.exec_icon) + ' Next</button>');
				$('#finish-' + stepId).on('click', function() {

					Command.setProperty(stepId, 'isSuspended', false, false, function() {

						_BPMN.enableContinuousUpdate(p.id);
					});
				});
			}
		});

	},
	enableContinuousUpdate: function(id) {

		if (_BPMN.timeouts[id]) {

			window.clearInterval(_BPMN.timeouts[id]);
		}

		if (id && id.length) {

			_BPMN.updateRow(id);

			_BPMN.timeouts[id] = window.setInterval(function() {
				_BPMN.updateRow(id);
			}, 200);

		}
	},
	stopAllTimeouts: function() {

		Object.keys(_BPMN.timeouts).forEach(function(key) {
			window.clearInterval(_BPMN.timeouts[key]);
		});
	}
}
