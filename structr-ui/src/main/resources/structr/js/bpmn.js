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

var main, bpmnMain, bpmnTree, bpmnContents, bpmnContext;
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

			main = document.querySelector('#main');
			main.innerHTML = html;

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

			$(window).on('keydown.search', function (e) {
				if (_BPMN.searchIsActive()) {
					if (e.key === 'Escape') {
						_BPMN.cancelSearch();
					}
				}
				;
			});

			var modeler = new BpmnJS({
				container: '#bpmn-contents',
				propertiesPanel: {
					parent: '#bpmn-context'
				}
			});

			modeler.on('selection.changed', (e) => {
				console.log(e);
			});

			modeler.on('element.changed', (e) => {
				console.log(e);
			});

			var xml = '<?xml version="1.0" encoding="UTF-8"?><bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Definitions_1au1qvg" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="3.0.0-dev"><bpmn:process id="Process_1ke4992" isExecutable="true"><bpmn:startEvent id="StartEvent_1" /></bpmn:process><bpmndi:BPMNDiagram id="BPMNDiagram_1"><bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1ke4992"><bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1"><dc:Bounds x="179" y="159" width="36" height="36" /></bpmndi:BPMNShape></bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>';

			modeler.importXML(xml, function (err) {
				console.log(err);
			});
		});

	},
	treeInitFunction: function (obj, callback) {

		switch (obj.id) {

			case '#':

				var defaultDiagramEntries = [
					{
						id: 'favorites',
						text: 'Favorites',
						children: false,
						icon: _Icons.star_icon
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

			default:
				break;
		}

	},
	displayProperties: function(event) {

	}
}
