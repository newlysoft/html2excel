/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.liaochong.html2excel.core;

import com.github.liaochong.html2excel.core.parser.HtmlTableParser;
import com.github.liaochong.html2excel.core.parser.Td;
import com.github.liaochong.html2excel.core.parser.Tr;
import com.github.liaochong.html2excel.core.style.BackgroundStyle;
import com.github.liaochong.html2excel.core.style.BorderStyle;
import com.github.liaochong.html2excel.core.style.FontStyle;
import com.github.liaochong.html2excel.core.style.TdDefaultCellStyle;
import com.github.liaochong.html2excel.core.style.TextAlignStyle;
import com.github.liaochong.html2excel.core.style.ThDefaultCellStyle;
import com.github.liaochong.html2excel.utils.TdUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author liaochong
 * @version 1.0
 */
public abstract class AbstractExcelFactory implements ExcelFactory {

    protected Workbook workbook;
    /**
     * 每行的单元格最大高度map
     */
    private Map<Integer, Short> maxTdHeightMap = new HashMap<>();
    /**
     * 是否使用默认样式
     */
    private boolean useDefaultStyle;
    /**
     * 自定义颜色索引
     */
    private AtomicInteger colorIndex = new AtomicInteger(56);
    /**
     * 单元格样式映射
     */
    private Map<Map<String, String>, CellStyle> cellStyleMap = new HashMap<>();
    /**
     * 样式容器
     */
    private Map<HtmlTableParser.TableTag, CellStyle> defaultCellStyleMap;
    /**
     * 字体map
     */
    private Map<String, Font> fontMap = new HashMap<>();
    /**
     * 冻结区域
     */
    private FreezePane[] freezePanes;
    /**
     * 内存数据保有量
     */
    private Integer rowAccessWindowSize = SXSSFWorkbook.DEFAULT_WINDOW_SIZE;

    @Override
    public ExcelFactory useDefaultStyle() {
        this.useDefaultStyle = true;
        return this;
    }

    @Override
    public ExcelFactory freezePanes(FreezePane... freezePanes) {
        this.freezePanes = freezePanes;
        return this;
    }

    @Override
    public ExcelFactory rowAccessWindowSize(int rowAccessWindowSize) {
        if (rowAccessWindowSize <= 0) {
            return this;
        }
        this.rowAccessWindowSize = rowAccessWindowSize;
        return this;
    }

    @Override
    public ExcelFactory workbookType(WorkbookType workbookType) {
        switch (workbookType) {
            case XLS:
                workbook = new HSSFWorkbook();
                break;
            case XLSX:
                workbook = new XSSFWorkbook();
                break;
            case SXLSX:
                workbook = new SXSSFWorkbook(rowAccessWindowSize);
                break;
            default:
                workbook = new XSSFWorkbook();
        }
        return this;
    }

    /**
     * 创建行-row
     *
     * @param tr    tr
     * @param sheet sheet
     */
    protected void createRow(Tr tr, Sheet sheet) {
        Row row = sheet.getRow(tr.getIndex());
        if (Objects.isNull(row)) {
            row = sheet.createRow(tr.getIndex());
        }
        for (Td td : tr.getTdList()) {
            this.createCell(td, sheet, row);
        }
        // 设置行高，最小12
        if (Objects.isNull(maxTdHeightMap.get(row.getRowNum()))) {
            row.setHeightInPoints(row.getHeightInPoints() + 5);
        } else {
            row.setHeightInPoints((short) (maxTdHeightMap.get(row.getRowNum()) + 5));
            maxTdHeightMap.remove(row.getRowNum());
        }
    }

    /**
     * 创建单元格
     *
     * @param td    td
     * @param sheet sheet
     */
    protected void createCell(Td td, Sheet sheet, Row currentRow) {
        Cell cell = currentRow.getCell(td.getCol());
        if (Objects.isNull(cell)) {
            cell = currentRow.createCell(td.getCol());
        }
        cell.setCellValue(td.getContent());

        // 设置单元格样式
        for (int i = td.getRow(), rowBound = td.getRowBound(); i <= rowBound; i++) {
            Row row = sheet.getRow(i);
            if (Objects.isNull(row)) {
                row = sheet.createRow(i);
            }
            for (int j = td.getCol(), colBound = td.getColBound(); j <= colBound; j++) {
                cell = row.getCell(j);
                if (Objects.isNull(cell)) {
                    cell = row.createCell(j);
                }
                this.setCellStyle(row, cell, td);
            }
        }
        if (td.getColSpan() > 0 || td.getRowSpan() > 0) {
            sheet.addMergedRegion(new CellRangeAddress(td.getRow(), td.getRowBound(), td.getCol(), td.getColBound()));
        }
    }

    /**
     * 设置单元格样式
     *
     * @param cell 单元格
     * @param td   td单元格
     */
    private void setCellStyle(Row row, Cell cell, Td td) {
        if (useDefaultStyle) {
            if (td.isTh()) {
                cell.setCellStyle(defaultCellStyleMap.get(HtmlTableParser.TableTag.th));
            } else {
                cell.setCellStyle(defaultCellStyleMap.get(HtmlTableParser.TableTag.td));
            }
        } else {
            String fs = td.getStyle().get("font-size");
            if (Objects.nonNull(fs)) {
                fs = fs.replaceAll("\\D*", "");
                short fontSize = Short.parseShort(fs);
                if (fontSize > maxTdHeightMap.getOrDefault(row.getRowNum(), FontStyle.DEFAULT_FONT_SIZE)) {
                    maxTdHeightMap.put(row.getRowNum(), fontSize);
                }
            }
            if (cellStyleMap.containsKey(td.getStyle())) {
                cell.setCellStyle(cellStyleMap.get(td.getStyle()));
                return;
            }
            CellStyle cellStyle = workbook.createCellStyle();
            // background-color
            BackgroundStyle.setBackgroundColor(workbook, cellStyle, td.getStyle(), colorIndex);
            // text-align
            TextAlignStyle.setTextAlign(cellStyle, td.getStyle());
            // border
            BorderStyle.setBorder(cellStyle, td.getStyle());
            // font
            FontStyle.setFont(workbook, cellStyle, td.getStyle(), fontMap);
            cell.setCellStyle(cellStyle);
            cellStyleMap.put(td.getStyle(), cellStyle);
        }
    }

    /**
     * 空工作簿
     *
     * @return Workbook
     */
    protected Workbook emptyWorkbook() {
        if (Objects.isNull(workbook)) {
            workbook = new XSSFWorkbook();
        }
        workbook.createSheet();
        return workbook;
    }

    /**
     * 初始化默认单元格样式
     */
    protected void initDefaultCellStyleMap() {
        if (useDefaultStyle) {
            defaultCellStyleMap = new EnumMap<>(HtmlTableParser.TableTag.class);
            defaultCellStyleMap.put(HtmlTableParser.TableTag.th, new ThDefaultCellStyle().supply(workbook));
            defaultCellStyleMap.put(HtmlTableParser.TableTag.td, new TdDefaultCellStyle().supply(workbook));
        }
    }

    /**
     * 窗口冻结
     *
     * @param tableIndex table index
     * @param sheet      sheet
     */
    protected void freezePane(int tableIndex, Sheet sheet) {
        if (Objects.nonNull(freezePanes) && freezePanes.length > tableIndex) {
            FreezePane freezePane = freezePanes[tableIndex];
            if (Objects.isNull(freezePane)) {
                throw new IllegalStateException("FreezePane is null");
            }
            sheet.createFreezePane(freezePane.getColSplit(), freezePane.getRowSplit());
        }
    }

    /**
     * 获取每列最大宽度
     *
     * @param trList trList
     */
    protected Map<Integer, Integer> getColMaxWidthMap(List<Tr> trList) {
        if (useDefaultStyle) {
            // 使用默认样式，需要重新修正加粗的标题自适应宽度
            trList.parallelStream().forEach(tr -> {
                tr.getTdList().stream().filter(Td::isTh).forEach(th -> {
                    int tdWidth = TdUtil.getStringWidth(th.getContent(), 0.1);
                    tr.getColWidthMap().put(th.getCol(), tdWidth);
                });
            });
        }
        int mapMaxSize = trList.stream().mapToInt(tr -> tr.getColWidthMap().size()).max().orElse(16);
        Map<Integer, Integer> colMaxWidthMap = new HashMap<>(mapMaxSize);
        trList.forEach(tr -> {
            tr.getColWidthMap().forEach((k, v) -> {
                Integer width = colMaxWidthMap.get(k);
                if (Objects.isNull(width) || v > width) {
                    colMaxWidthMap.put(k, v);
                }
            });
            tr.setColWidthMap(null);
        });
        return colMaxWidthMap;
    }

    /**
     * 设置每列宽度
     *
     * @param colMaxWidthMap 列最大宽度Map
     * @param sheet          sheet
     */
    protected void setColWidth(Map<Integer, Integer> colMaxWidthMap, Sheet sheet) {
        colMaxWidthMap.forEach((key, value) -> {
            int contentLength = value << 1;
            if (contentLength > 255) {
                contentLength = 255;
            }
            sheet.setColumnWidth(key, contentLength << 8);
        });
    }
}
