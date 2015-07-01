/**
 * 
 */
package com.fccfc.framework.task.service.impl;

import java.util.List;

import javax.annotation.Resource;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.ListenerManager;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

import com.fccfc.framework.common.ErrorCodeDef;
import com.fccfc.framework.common.ServiceException;
import com.fccfc.framework.common.utils.Assert;
import com.fccfc.framework.common.utils.CommonUtil;
import com.fccfc.framework.db.core.DaoException;
import com.fccfc.framework.task.TaskConstants;
import com.fccfc.framework.task.bean.CronTriggerPojo;
import com.fccfc.framework.task.bean.SimpleTriggerPojo;
import com.fccfc.framework.task.bean.TaskPojo;
import com.fccfc.framework.task.bean.TriggerPojo;
import com.fccfc.framework.task.dao.JobDao;
import com.fccfc.framework.task.dao.TriggerDao;
import com.fccfc.framework.task.job.JobExcutor;
import com.fccfc.framework.task.job.SynchronizedJobExcutor;
import com.fccfc.framework.task.listener.TaskListener;
import com.fccfc.framework.task.service.TaskService;

/**
 * <Description> <br>
 * 
 * @author 王伟<br>
 * @version 1.0<br>
 * @taskId <br>
 * @CreateDate 2014年11月5日 <br>
 * @since V1.0<br>
 * @see com.fccfc.framework.task.listener <br>
 */
public class TaskServiceImpl implements TaskService {
    @Resource
    private JobDao jobDao;

    @Resource
    private TriggerDao triggerDao;

    @Resource
    private Scheduler scheduler;

    @Resource
    private TaskListener taskListener;

    /*
     * (non-Javadoc)
     * @see com.fccfc.framework.api.task.TaskService#scheduleAllTask()
     */
    @Override
    public void scheduleAllTask() throws ServiceException {
        try {
            TaskPojo pojo = new TaskPojo();
            pojo.setTaskState(TaskPojo.TASK_STATE_ACQUIRED);
            List<TaskPojo> taskList = jobDao.selectTaskList(pojo, -1, -1);
            if (CommonUtil.isNotEmpty(taskList)) {
                for (TaskPojo task : taskList) {
                    List<TriggerPojo> triggerList = triggerDao.selectTriggerByTaskId(task.getTaskId());
                    if (CommonUtil.isNotEmpty(triggerList)) {
                        for (TriggerPojo trigger : triggerList) {
                            if (TriggerPojo.TRIGGER_TYPE_SIMPLE.equals(trigger.getTriggerType())) {
                                SimpleTriggerPojo triggerPojo = triggerDao.getSimpleTriggerById(trigger.getTriggerId());
                                schedule(task, triggerPojo);
                            }
                            else {
                                CronTriggerPojo triggerPojo = triggerDao.getCronTriggerById(trigger.getTriggerId());
                                schedule(task, triggerPojo);
                            }
                        }
                    }
                }
            }
        }
        catch (DaoException e) {
            throw new ServiceException("执行任务失败", e);
        }

    }

    /*
     * (non-Javadoc)
     * @see com.fccfc.framework.api.task.TaskService#schedule(com.fccfc.framework.common.bean.task.TaskPojo,
     * com.fccfc.framework.common.bean.task.SimpleTriggerPojo)
     */
    @Override
    public void schedule(TaskPojo taskPojo, SimpleTriggerPojo simpleTriggerPojo) throws ServiceException {
        try {
            Assert.notNull(taskPojo, "任务不能为空");
            Assert.notNull(simpleTriggerPojo, "触发器不能为空");

            JobDetail jobDetail = getJobDetail(taskPojo);

            SimpleScheduleBuilder builder = SimpleScheduleBuilder.simpleSchedule()
                .withRepeatCount(simpleTriggerPojo.getTimes()).withMisfireHandlingInstructionNextWithExistingCount()
                .withIntervalInSeconds(simpleTriggerPojo.getExecuteInterval());

            TriggerKey triggerKey = new TriggerKey(simpleTriggerPojo.getTriggerName(), taskPojo.getTaskName());
            Trigger trigger = TriggerBuilder.newTrigger().startAt(simpleTriggerPojo.getBeginTime())
                .endAt(simpleTriggerPojo.getEndTime()).withIdentity(triggerKey).withSchedule(builder).build();

            if (taskListener != null) {
                ListenerManager listenerManager = scheduler.getListenerManager();
                listenerManager.addJobListener(taskListener);
                listenerManager.addTriggerListener(taskListener);
                listenerManager.addSchedulerListener(taskListener);
            }

            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
            }
            else {
                scheduler.scheduleJob(jobDetail, trigger);
            }
        }
        catch (Exception e) {
            throw new ServiceException(ErrorCodeDef.SCHEDULE_TASK_ERROR_10021, "执行任务失败", e);
        }
    }

    /*
     * (non-Javadoc)
     * @see com.fccfc.framework.api.task.TaskService#schedule(com.fccfc.framework.common.bean.task.TaskPojo,
     * com.fccfc.framework.common.bean.task.CronTriggerPojo)
     */
    @Override
    public void schedule(TaskPojo taskPojo, CronTriggerPojo cronTriggerPojo) throws ServiceException {
        try {
            Assert.notNull(taskPojo, "任务不能为空");
            Assert.notNull(cronTriggerPojo, "触发器不能为空");

            JobDetail jobDetail = getJobDetail(taskPojo);

            TriggerKey triggerKey = new TriggerKey(cronTriggerPojo.getTriggerName(), taskPojo.getTaskName());

            CronScheduleBuilder cronScheduleBuiler = CronScheduleBuilder.cronSchedule(cronTriggerPojo
                .getCronExpression());

            Trigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey).withSchedule(cronScheduleBuiler)
                .build();

            if (taskListener != null) {
                ListenerManager listenerManager = scheduler.getListenerManager();
                listenerManager.addJobListener(taskListener);
                listenerManager.addTriggerListener(taskListener);
                listenerManager.addSchedulerListener(taskListener);
            }

            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
            }
            else {
                scheduler.scheduleJob(jobDetail, trigger);
            }
        }
        catch (Exception e) {
            throw new ServiceException(ErrorCodeDef.SCHEDULE_TASK_ERROR_10021, "执行任务失败", e);
        }
    }

    /*
     * (non-Javadoc)
     * @see com.fccfc.framework.api.task.TaskService#pause(com.fccfc.framework.common.bean.task.TaskPojo)
     */
    @Override
    public void pause(TaskPojo taskPojo) throws ServiceException {
        try {
            List<TriggerPojo> triggerList = triggerDao.selectTriggerByTaskId(taskPojo.getTaskId());
            if (CommonUtil.isNotEmpty(triggerList)) {
                for (TriggerPojo trigger : triggerList) {
                    scheduler.pauseTrigger(new TriggerKey(trigger.getTriggerName(), taskPojo.getTaskName()));
                }
            }
        }
        catch (Exception e) {
            throw new ServiceException(ErrorCodeDef.PAUSE_TASK_ERROR_10022, "暂停任务失败", e);
        }
    }

    /*
     * (non-Javadoc)
     * @see com.fccfc.framework.api.task.TaskService#resume(com.fccfc.framework.common.bean.task.TaskPojo)
     */
    @Override
    public void resume(TaskPojo taskPojo) throws ServiceException {
        try {
            List<TriggerPojo> triggerList = triggerDao.selectTriggerByTaskId(taskPojo.getTaskId());
            if (CommonUtil.isNotEmpty(triggerList)) {
                for (TriggerPojo trigger : triggerList) {
                    scheduler.resumeTrigger(new TriggerKey(trigger.getTriggerName(), taskPojo.getTaskName()));
                }
            }
        }
        catch (Exception e) {
            throw new ServiceException(ErrorCodeDef.RESUME_TASK_ERROR_10023, "暂停任务失败", e);
        }
    }

    /*
     * (non-Javadoc)
     * @see com.fccfc.framework.api.task.TaskService#remove(com.fccfc.framework.common.bean.task.TaskPojo)
     */
    @Override
    public void remove(TaskPojo taskPojo) throws ServiceException {
        try {
            List<TriggerPojo> triggerList = triggerDao.selectTriggerByTaskId(taskPojo.getTaskId());
            if (CommonUtil.isNotEmpty(triggerList)) {
                TriggerKey key = null;
                for (TriggerPojo trigger : triggerList) {
                    key = new TriggerKey(trigger.getTriggerName(), taskPojo.getTaskName());
                    scheduler.resumeTrigger(key);
                    scheduler.unscheduleJob(key);
                }
            }
        }
        catch (Exception e) {
            throw new ServiceException(ErrorCodeDef.REMOVE_TASK_ERROR_10024, "暂停任务失败", e);
        }
    }

    private JobDetail getJobDetail(TaskPojo taskPojo) {
        JobDetail detail = JobBuilder
            .newJob("Y".equals(taskPojo.getIsConcurrent()) ? JobExcutor.class : SynchronizedJobExcutor.class)
            .withIdentity(taskPojo.getTaskName(), taskPojo.getModuleCode()).build();
        detail.isDurable();
        JobDataMap dataMap = detail.getJobDataMap();
        dataMap.put(TaskConstants.TASK_CLASS_NAME, taskPojo.getClassName());
        dataMap.put(TaskConstants.TASK_EXCUTE_METHOD_NAME, taskPojo.getMethod());
        dataMap.put(TaskConstants.TASK_ID, taskPojo.getTaskId());
        return detail;
    }

    public void setTaskListener(TaskListener taskListener) {
        this.taskListener = taskListener;
    }
}