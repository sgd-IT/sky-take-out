package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 套餐业务实现类
 */
@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐及其关联的菜品
     *
     * @param setmealDTO 套餐数据传输对象，包含套餐基本信息和关联的菜品列表
     */
    @Override
    public void addWithDishes(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        // 插入套餐基本信息
        setmealMapper.insert(setmeal);

        //获取新增套餐的id
        Long setmealId = setmeal.getId();

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        // 如果套餐关联了菜品，则批量插入套餐菜品关联数据
        if (setmealDishes != null && setmealDishes.size() > 0) {
            setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmealId));
            setmealDishMapper.insertBatch(setmealDishes);
        }

    }

    /**
     * 分页查询
     *
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());

        Page<Setmeal> page = setmealMapper.pageQuery(setmealPageQueryDTO);

        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * @param ids
     */
    @Override
    public void delete(List<Long> ids) {
        ids.forEach(id -> {
            Setmeal setmeal = setmealMapper.getById(id);
            if (setmeal.getStatus() == 1) {
                throw new RuntimeException(MessageConstant.SETMEAL_ON_SALE);
            }

            setmealMapper.deleteById(id);
            setmealDishMapper.deleteBySetmealId(id);
        });
    }

    /**
     * 根据id查询套餐
     *
     * @param id 套餐ID
     * @return 套餐信息VO对象，包含套餐基本信息和关联的菜品信息
     */
    @Override
    public SetmealVO getById(Long id) {
        // 查询套餐基本信息
        Setmeal setmeal = setmealMapper.getById(id);

        // 复制套餐基本信息到VO对象
        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal, setmealVO);

        // 查询并设置套餐关联的菜品信息
        setmealVO.setSetmealDishes(setmealDishMapper.getBySetmealId(id));

        return setmealVO;
    }

    /**
     * 修改套餐信息
     *
     * @param setmealDTO 套餐数据传输对象
     */
    @Override
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        // 更新套餐基本信息
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.update(setmeal);

        // 重新关联菜品信息：先删除原有关系，再插入新关系
        setmealDishMapper.deleteBySetmealId(setmealDTO.getId());

        // 获取套餐菜品列表
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        // 如果套餐菜品列表不为空，则批量插入套餐菜品关联数据
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            // 为每个套餐菜品设置套餐ID
            setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmealDTO.getId()));
            // 批量插入套餐菜品关联数据
            setmealDishMapper.insertBatch(setmealDishes);

        }
    }

    /**
     * 启用或停用套餐
     *
     * @param status 套餐状态，ENABLE为启用，DISABLE为停用
     * @param id     套餐ID
     */
    @Override
    public void startOrStop(Integer status, Long id) {
//        - 可以对状态为起售的套餐进行停售操作，可以对状态为停售的套餐进行起售操作
//        - 起售套餐时，如果套餐内包含停售的菜品，则不能起售

        // 检查启用套餐的条件：如果要启用套餐，需要确保套餐内所有菜品都处于启用状态
        if (status == StatusConstant.ENABLE) {
            List<Dish> dishlist = dishMapper.getBySetmealId(id);
            if (dishlist != null && dishlist.size() > 0) {
                dishlist.forEach(dish -> {
                    if (dish.getStatus() == StatusConstant.DISABLE) {
                        throw new RuntimeException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                });
            }
        }
//        //TODO 起售的套餐可以展示在用户端，停售的套餐不能展示在用户端

        // 更新套餐状态
        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);
    }

    /**
     * 条件查询
     *
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     *
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }

}
