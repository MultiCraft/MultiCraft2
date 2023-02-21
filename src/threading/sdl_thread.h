/*
MultiCraft
Copyright (C) 2000-2006  Jori Liesenborgs <jori.liesenborgs@gmail.com>
Copyright (C) 2023 Dawid Gan <deveee@gmail.com>

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation; either version 3.0 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License along
with this program; if not, write to the Free Software Foundation, Inc.,
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*/

#pragma once

#include "IrrCompileConfig.h"

#ifdef _IRR_COMPILE_WITH_SDL_DEVICE_

#include "util/basic_macros.h"

#include <string>
#include <atomic>
#include <mutex>

#include <SDL.h>

class Thread
{
public:
	Thread(const std::string &name = "");
	virtual ~Thread();
	DISABLE_CLASS_COPY(Thread)

	/*
	 * Begins execution of a new thread at the pure virtual method Thread::run().
	 * Execution of the thread is guaranteed to have started after this function
	 * returns.
	 */
	bool start();

	/*
	 * Requests that the thread exit gracefully.
	 * Returns immediately; thread execution is guaranteed to be complete after
	 * a subsequent call to Thread::wait.
	 */
	bool stop();

	/*
	 * Waits for thread to finish.
	 * Note:  This does not stop a thread, you have to do this on your own.
	 * Returns false immediately if the thread is not started or has been waited
	 * on before.
	 */
	bool wait();

	/*
	 * Returns true if the calling thread is this Thread object.
	 */
	bool isCurrentThread() { return SDL_ThreadID() == getThreadId(); }

	bool isRunning() { return m_running; }
	bool stopRequested() { return m_request_stop; }

	SDL_threadID getThreadId() { return SDL_GetThreadID(m_thread_obj); }

	/*
	 * Gets the thread return value.
	 * Returns true if the thread has exited and the return value was available,
	 * or false if the thread has yet to finish.
	 */
	bool getReturnValue(void **ret);

	/*
	 * Binds (if possible, otherwise sets the affinity of) the thread to the
	 * specific processor specified by proc_number.
	 */
	bool bindToProcessor(unsigned int proc_number);

	/*
	 * Sets the thread priority to the specified priority.
	 *
	 * prio can be one of: SDL_THREAD_PRIORITY_LOW, SDL_THREAD_PRIORITY_NORMAL,
	 * SDL_THREAD_PRIORITY_HIGH.
	 */
	bool setPriority(SDL_ThreadPriority prio);

	/*
	 * Sets the currently executing thread's name to where supported; useful
	 * for debugging.
	 */
	static void setName(const std::string &name);

	/*
	 * Returns the number of processors/cores configured and active on this machine.
	 */
	static unsigned int getNumberOfProcessors();

protected:
	std::string m_name;

	virtual void *run() = 0;

private:
	static int threadProc(void *data);

	void *m_retval = nullptr;
	bool m_joinable = false;
	std::atomic<bool> m_request_stop;
	std::atomic<bool> m_running;
	std::mutex m_mutex;
	std::mutex m_start_finished_mutex;

	SDL_Thread *m_thread_obj = nullptr;
};

#endif
