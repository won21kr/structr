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
						icon: _Icons.exec_icon
					},
					{
						id: 'root',
						text: 'BPMN Process Definitions',
						children: [
							{
								id: 'deployed',
								text: 'Disabled Process Definitions',
								children: true,
								icon: _Icons.exec_blue_icon
							},
							{
								id: 'active',
								text: 'Enabled Process Definitions',
								children: true,
								icon: _Icons.exec_icon
							}
						],
						icon: _Icons.structr_logo_small
					}
				];

				callback(defaultDiagramEntries);
				break;

			case 'deployed':
				_BPMN.treeAddProcesses(false, callback);
				break;

			case 'active':
				_BPMN.treeAddProcesses(true, callback);
				break;

			default:
				_BPMN.treeAddSteps(obj.id, callback);
				break;
		}

	},
	refreshTree: function() {
		_TreeHelper.refreshTree(bpmnTree);
	},
	handleTreeClick: function(event, data) {

		console.log(data.node);

		switch (data.node.id) {

			case 'running':
				_BPMN.showManageProcesses();
				break;

			default:
				if (data.node.data && data.node.data.type) {
					switch (data.node.data.type) {
						case 'process':
							_BPMN.showProcessDetails(data.node.data.id);
							break;
						case 'step':
							_BPMN.showStepDetails(data.node.id);
							break;
					}
				}
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

					Structr.fetchHtmlTemplate('bpmn/available-process-table-row', { process: p, info: p.info }, function (html) {

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

			}, true);


			//query: function(type, pageSize, page, sort, order, properties, callback, exact, view, customView) {
			Command.query('BPMNProcess', 1000, 1, 'createdDate', 'desc', undefined, function(result) {

				let table = $('#bpmn-running-process-list');

				result.forEach(function(p) {

					_BPMN.appendRow(table, p);
				});

			}, true, 'public');


		});

	},
	showStepDetails: function(id) {

		Command.get(id, undefined, function(step) {

			let action        = '';
			let canBeExecuted = '';

			step.schemaMethods.forEach(m => {
				switch (m.name) {
					case 'action':
						action = m.source;
						break;

					case 'canBeExecuted':
						canBeExecuted = m.source;
				}
			});

			Structr.fetchHtmlTemplate('bpmn/step-details', { step: step, action: action, canBeExecuted: canBeExecuted }, function (html) {

				$('#bpmn-contents').empty();
				$('#bpmn-contents').append(html);

				CodeMirror.fromTextArea(document.getElementById('action-' + step.id), {
					mode: 'javascript',
					lineNumbers: true,
					lineWrapping: false,
					indentUnit: 4,
					tabSize:4,
					indentWithTabs: true
				}).setSize(null, 150);

				CodeMirror.fromTextArea(document.getElementById('exec-' + step.id), {
					mode: 'javascript',
					lineNumbers: true,
					lineWrapping: false,
					indentUnit: 4,
					tabSize:4,
					indentWithTabs: true
				}).setSize(null, 150);
			});

		});

	},
	showProcessDetails: function(id) {

		Command.get(id, undefined, function(process) {

			console.log(process);

			Structr.fetchHtmlTemplate('bpmn/process-details', { process: process }, function (html) {

				$('#bpmn-contents').empty();
				$('#bpmn-contents').append(html);

				if (process.implementsInterfaces === 'org.structr.bpmn.model.BPMNInactive') {

					Structr.fetchHtmlTemplate('code/action-button', { suffix: 'activate', icon: 'plus-circle', name: 'Enable this process' }, function (html) {

						$('#process-contents').append(html);

						$('#action-button-activate').on('click', function() {

							Structr.confirmation('<h3>Really enable this process?</h3><p>The process will be made available and is ready to use.</p>',
								function() {
									Structr.showLoadingMessage('Schema is compiling..', 'Please wait.', 200);
									Command.setProperties(id, { implementsInterfaces: null }, function() {
										_BPMN.refreshTree();
										Structr.hideLoadingMessage();
									});
								}
							);

						});

					});

				} else {

					Structr.fetchHtmlTemplate('code/action-button', { suffix: 'deactivate', icon: 'minus-circle', name: 'Disable this process' }, function (html) {

						$('#process-contents').append(html);

						$('#action-button-deactivate').on('click', function() {

							Structr.confirmation('<h3>Really disable this process?</h3><p>No new process instances can be created.</p>',
								function() {
									Structr.showLoadingMessage('Schema is compiling.', 'Please wait..', 200);
									Command.setProperties(id, { implementsInterfaces: 'org.structr.bpmn.model.BPMNInactive' }, function() {
										_BPMN.refreshTree();
										Structr.hideLoadingMessage();
									});
								}
							);

						});

					});
				}

				Structr.fetchHtmlTemplate('code/action-button', { suffix: 'delete', icon: 'remove red', name: 'Delete this process' }, function (html) {

					$('#process-contents').append(html);

					$('#action-button-delete').on('click', function() {

						Structr.confirmation('<h3>Really delete process?</h3><p>blah</p>',
							function() {
								Structr.showLoadingMessage('Schema is compiling..', 'Please wait.', 200);
								Command.deleteNode(id, false, function() {
									_BPMN.refreshTree();
									Structr.hideLoadingMessage();
								});
							}
						);

					});
				});

			}, 'ui');

		});

	},
	updateRow: function(id) {

		Command.get(id, "id,type,info", function(p) {

			_BPMN.appendRow($('#row-' + id), p, true);
		});
	},
	appendRow: function(container, p, replace) {

		Structr.fetchHtmlTemplate('bpmn/running-process-table-row', { process: p, info: p.info }, function (html) {

			if (replace) {
				container.replaceWith(html);
			} else {
				container.append(html);
			}

			if (p && p.info && p.info.currentStep && !p.info.finished && p.info.suspended) {

				let stepId = p.info.currentStep.id;

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
	},
	treeAddProcesses: function(active, callback) {

		let searchData = {
			extendsClass: 'org.structr.bpmn.model.BPMNProcess',
			implementsInterfaces: active ? '' : 'org.structr.bpmn.model.BPMNInactive'
		};

		Command.query('SchemaNode', 1000, 1, 'name', 'asc', searchData, function(result) {

			let processes = [];

			result.forEach(p => {

				processes.push({
					id: p.category,
					text: p.category || p.name,
					children: true,
					icon: _Icons.folder_icon,
					data: {
						type: 'process',
						id: p.id
					}
				});
			});

			callback(processes);

		}, true);
	},
	treeAddSteps: function(id, callback) {

	    //query: function(type, pageSize, page, sort, order, properties, callback, exact, view, customView) {
		Command.query('SchemaNode', 1000, 1, 'description', 'asc', { category: id }, function(result) {

			let index = {};
			let steps = [];

			result.forEach(s => {
				index[s.id] = s;
			});

			result.forEach(p => {

				let level = _BPMN.determineHierarchy(index, p);

				steps.push({
					id: p.id,
					text: level + '. ' + (p.description || p.name),
					level: level,
					icon: _Icons.brick_icon,
					data: {
						type: 'step'
					}
				});
			});

			steps.sort((a, b) => {
				if (a.level < b.level) { return -1; };
				if (a.level > b.level) { return  1; };
				return 0;
			});

			callback(steps);


		}, true, 'ui');
	},
	determineHierarchy: function(index, step) {

		let current = step;
		let level   = 1;

		while (current && current.relatedFrom && current.relatedFrom.length && level < 100) {

			let id = current.relatedFrom[0].sourceId;
			current = index[id];
			level++;
		}

		return level;
	},
	showProcess: function(id) {

	    //query: function(type, pageSize, page, sort, order, properties, callback, exact, view, customView) {
		Command.query('SchemaNode', 1000, 1, 'description', 'asc', { category: id }, function(result) {

			let index = {};
			let steps = [];

			result.forEach(s => {
				index[s.id] = s;
			});

			result.forEach(s => {

				let level = _BPMN.determineHierarchy(index, s);

				s.level = level;

				steps.push(s);

			});

			steps.sort((a, b) => {
				if (a.level < b.level) { return -1; };
				if (a.level > b.level) { return  1; };
				return 0;
			});

			let container = $('#bpmn-contents');

			Structr.fetchHtmlTemplate('bpmn/steps', { }, function (html) {

				container.empty();
				container.append(html);

				stepContainer = $('#bpmn-process-step-list');

				steps.forEach(s => {

					let action        = '';
					let canBeExecuted = '';

					s.schemaMethods.forEach(m => {
						switch (m.name) {
							case 'action':
								action = m.source;
								break;

							case 'canBeExecuted':
								canBeExecuted = m.source;
						}
					});

					Structr.fetchHtmlTemplate('bpmn/process-step-table-row', { step: s, action: action, canBeExecuted: canBeExecuted }, function (html) {

						stepContainer.append(html);

						CodeMirror.fromTextArea(document.getElementById('action-' + s.id), {
							mode: 'javascript',
							lineNumbers: true,
							lineWrapping: false,
							indentUnit: 4,
							tabSize:4,
							indentWithTabs: true
						}).setSize(null, 100);

						CodeMirror.fromTextArea(document.getElementById('exec-' + s.id), {
							mode: 'javascript',
							lineNumbers: true,
							lineWrapping: false,
							indentUnit: 4,
							tabSize:4,
							indentWithTabs: true
						}).setSize(null, 100);

						s.schemaProperties.forEach(p => {

							$('#properties-' + s.id).append(p.name + ': ' + p.propertyType);
						});

					});
				});
			});

		}, true, 'ui');
	}
}
