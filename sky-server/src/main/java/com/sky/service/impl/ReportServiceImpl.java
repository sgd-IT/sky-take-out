package com.sky.service.impl;


import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.EmployeeMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 营业额统计
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO turnoverStatistics(LocalDate begin, LocalDate end) {
        //当前集合用于存放begin到end范围的每天日期
        List<LocalDate> dateList = getLocalDates(begin, end);

        //当前集合用于存放begin到end范围的每天营业额
        List<Double> turnoverList = new ArrayList<>();

        for (LocalDate date : dateList) {
            //查询date日期对应的营业额，营业额状态为（已完成）的订单
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map<String, Object> map = new HashMap<>();
            map.put("begin", beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            //如果为null，则设置为0.0
            turnover = turnover == null ? 0.0 : turnover;
            turnoverList.add(turnover);
        }

        //封装返回结果
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }

    /**
     * 用户统计
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO userStatistics(LocalDate begin, LocalDate end) {
        //当前集合用于存放begin到end范围的每天日期
        List<LocalDate> dateList = getLocalDates(begin, end);

        //当前集合用于存放begin到end范围的每天总用户数量
        List<Integer> totalUserList = new ArrayList<>();
        //当前集合用于存放begin到end范围的每天新增用户数量
        List<Integer> newUserList = new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map<String, Object> map = new HashMap<>();
            map.put("end", endTime);
            //查询date日期对应的总用户数量
            Integer totalUserCount = userMapper.countByMap(map);
            totalUserCount = totalUserCount == null ? 0 : totalUserCount;

            //查询date日期对应新增用户数量
            map.put("begin", beginTime);
            Integer newUserCount = userMapper.countByMap(map);
            newUserCount = newUserCount == null ? 0 : newUserCount;

            totalUserList.add(totalUserCount);
            newUserList.add(newUserCount);

        }
        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();
    }

    /**
     * 订单统计
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO orderStatistics(LocalDate begin, LocalDate end) {
        //当前集合用于存放begin到end范围的每天日期
        List<LocalDate> dateList = getLocalDates(begin, end);

        //当前集合用于存放begin到end范围的每天订单数量
        List<Integer> orderCountList = new ArrayList<>();
        //当前集合用于存放begin到end范围的每天有效订单数量
        List<Integer> validOrderCountList = new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map<String, Object> map = new HashMap<>();
            map.put("begin", beginTime);
            map.put("end", endTime);
            //查询date日期对应的订单数量
            Integer orderCount = orderMapper.countByMap(map);
            orderCount = orderCount == null ? 0 : orderCount;

            map.put("status", Orders.COMPLETED);
            //查询date日期对应的有效订单数量
            Integer validOrderCount = orderMapper.countByMap(map);
            validOrderCount = validOrderCount == null ? 0 : validOrderCount;

            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
        }

        //计算时间区间内的订单总数量
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();//reduce方法就是把集合中的元素进行合并，聚合后的结果作为最终结果返回
        //计算时间区间内的有效订单数量
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();
        //计算订单完成率
        Double orderCompletionRate = 0.0;
        if (totalOrderCount != 0) {
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }

        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 销量排名top10
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);

        String nameList = StringUtils.join(salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList()), ",");
        String numberList = StringUtils.join(salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList()), ",");

        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    /**
     * 获取指定时间范围内的日期
     *
     * @param begin
     * @param end
     * @return
     */
    private static List<LocalDate> getLocalDates(LocalDate begin, LocalDate end) {
        //当前集合用于存放begin到end范围的每天日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);

        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        return dateList;
    }

    /**
     * 导出运营数据报表到Excel文件并写入HTTP响应流中
     *
     * @param response HTTP响应对象，用于将生成的Excel文件输出给客户端
     */
    @Override
    public void export(HttpServletResponse response) {
        // 查询数据库，获取营业数据 -- 查询最近30天的运营数据
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);

        // 查出概览数据（时间段为过去30天）
        BusinessDataVO businessData = workspaceService.getBusinessData(
                LocalDateTime.of(begin, LocalTime.MIN),
                LocalDateTime.of(end, LocalTime.MAX)
        );

        // 通过POI写入Excel文件中
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");

        // 基于模板文件创建一个新的Excel文件
        try {
            XSSFWorkbook excel = new XSSFWorkbook(inputStream);

            // 填充概览数据
            XSSFSheet sheetAt = excel.getSheetAt(0); // 获取第一个sheet
            sheetAt.getRow(1).getCell(1).setCellValue("时间：" + begin + "至" + end); // 设置时间范围

            // 填充第四行的概览指标
            XSSFRow row = sheetAt.getRow(3);
            row.getCell(2).setCellValue(businessData.getTurnover());           // 营业额
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate()); // 订单完成率
            row.getCell(6).setCellValue(businessData.getNewUsers());           // 新增用户数

            // 填充第五行的概览指标
            row = sheetAt.getRow(4);
            row.getCell(2).setCellValue(businessData.getValidOrderCount());     // 有效订单数
            row.getCell(4).setCellValue(businessData.getUnitPrice());          // 平均客单价

            // 填充每日明细数据（从第8行开始）
            for (int i = 0; i < 30; i++) {
                LocalDate date = begin.plusDays(i);
                BusinessDataVO data = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));

                row = sheetAt.getRow(7 + i);
                row.getCell(1).setCellValue(date.toString());                 // 日期
                row.getCell(2).setCellValue(data.getTurnover());              // 营业额
                row.getCell(3).setCellValue(data.getValidOrderCount());       // 有效订单数
                row.getCell(4).setCellValue(data.getOrderCompletionRate());   // 订单完成率
                row.getCell(5).setCellValue(data.getNewUsers());              // 新增用户数
                row.getCell(6).setCellValue(data.getUnitPrice());             // 平均客单价
            }

            // 将Excel文件写入响应输出流
            ServletOutputStream outputStream = response.getOutputStream();
            excel.write(outputStream);

            // 关闭资源
            outputStream.close();
            excel.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
