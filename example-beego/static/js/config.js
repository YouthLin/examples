function fixHead($table) {
    var topValue = $table.data('top-value') || 56;
    var target = $table.data('target');
    var $target = $(target);
    // console.log('fixHead:' + target + "=" + topValue);
    $(window).scroll(function () {
        var topDis = $table.offset().top - $(window).scrollTop();
        var bottomDis = $table.height() + $table.offset().top - $(window).scrollTop() - $target.height();
        if (topDis < topValue && bottomDis > topValue) {
            $target.removeClass('invisible');
            $target.css('top', topValue + 'px');
        } else {
            $target.css('top', 0);
            $target.addClass('invisible');
        }
    });
}

function toggleAside($ctl) {
    var lg = $($ctl.data('lg'));
    var sm = $($ctl.data('sm'));
    if (lg.hasClass('col-8')) {
        lg.removeClass('col-8').addClass('col-12');
        sm.removeClass('col-4').addClass('d-none');
    } else {
        lg.removeClass('col-12').addClass('col-8');
        sm.removeClass('d-none').addClass('col-4');
    }
}

$(document).ready(function () {
    $('.fix-head-when-scroll').each(function () {
        fixHead($(this));
    });
    $('.aside-ctl').click(function () {
        toggleAside($(this));
    });
    // 页面就绪滑动到锚点(如果有)
    scrollToAnchor();
    // 锚点滑动
    $('a[href^="#"]').click(function () {
        scrollToAnchor($(this).attr('href'));
        return false;
    });

    $('.date').each(function () {
        var date = $(this).html();
        $(this).html(new Date(date).toLocaleString());
        $(this).attr("title", date);
    });

    suggest();

    if (active === undefined) {
    } else {
        activeNavItem(active);
    }

    showJson();
});

function activeNavItem(className) {
    $(document).ready(function () {
        $('.' + className).addClass('active');
    });
}

function timezoneName() {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

function timezoneMinute() {
    return new Date().getTimezoneOffset();
}

function scrollToAnchor(hash) {
    hash = hash || window.location.hash;
    if (hash === undefined || hash.length === 0) {
        return;
    }
    var $target = $(hash);
    var more = $target.data('top') || 60;
    scroll($target.offset().top - more);
}

function scroll(top) {
    $('html, body').animate({
        scrollTop: top
    }, 350);
}

//define function to calc character length
function chEnWordCount(str) {
    var ch = 0, en = 0;
    if (str.match(/[\u4e00-\u9fa5]+/g)) {
        ch = str.match(/[\u4e00-\u9fa5]/g).length
    }
    if (str.match(/[a-zA-Z]+/g)) {
        en = str.match(/[a-zA-Z]/g).length
    }
    return {ch: ch, en: en}
}

function suggest() {
    $(document).click(function () {
        $('.suggest').hide();
    });
    $('form.search-form').each(function () {
        debug("suggest...");
        //阻止表单提交
        $(this).submit(function () {
            return false
        });
        //搜索城市结果建议
        var $target = $($(this).data('target'));
        debug("target=" + $target);
        var cityCode;
        //监听输入框键盘输入
        $(this).bind("keyup", function (e) {
            //方向键先处理
            var key = e.which;
            if (key === 13) {//enter
                debug("cityCode="+cityCode);
                if (cityCode.toString().length > 4) {
                    window.location.href = '/weather/' + cityCode + ".html";
                } else {
                    window.location.href = '/cityList#p-' + cityCode;
                }
                return false;
            }
            if (key === 40 || key === 38 || key === 37 || key === 39) {// 40-down 38-up 37-left 39-right
                var wrap = $target.find('ul');
                var current = wrap.find('li.hover');
                if (key === 38) {//ip
                    if (current.length > 0) {
                        var prev = current.removeClass('hover').prev();
                        if (prev.length > 0) {
                            prev.addClass('hover');
                            current = prev;
                            $(this).val(prev.data('text'));
                        }
                    } else {
                        var last = wrap.find('li:last');
                        last.addClass('hover');
                        current = last;
                        $(this).val(last.data('text'));
                    }
                } else if (key === 40) {//down
                    if (current.length > 0) {
                        var next = current.removeClass('hover').next();
                        if (next.length > 0) {
                            next.addClass('hover');
                            current = next;
                            $(this).val(next.data('text'));
                        }
                    } else {
                        var first = wrap.find('li:first');
                        first.addClass('hover');
                        current = first;
                        $(this).val(first.data('text'));
                    }
                }
                cityCode = (current.data('code'));
                debug("wrap=" + wrap.offset().top + "current=" + current.offset().top + "wrap.scrollTop=" + wrap.scrollTop() + " code=" + cityCode);
                //如何通过 JQuery 将 DIV 的滚动条滚动到指定的位置
                //http://www.cnblogs.com/jaxu/archive/2013/05/17/3083019.html
                wrap.animate({scrollTop: current.offset().top - wrap.offset().top + wrap.scrollTop() - 70});
                //wrap.scrollTop(current.offset().top - wrap.offset().top + wrap.scrollTop() - 70);
                return false;
            }
            // 不是方向键 是输入内容
            var text = $(this).find('input[name=city]').val();
            debug("input text=" + text);
            if (text === "") {
                //没有输入字符 隐藏
                $target.hide();
                return false;
            }
            //加入字符检查
            if (!text.match(/^[\u4e00-\u9fa5|a-zA-Z]*$/)) {
                $target.hide();
                return false;
            } else {
                //calc input char length
                var countArray = chEnWordCount(text);
                if (countArray.ch > 0) {
                } else {
                    return false;
                }
            }
            debug("before ajax get");
            //异步请求
            var action = $(this).attr("action") + "?q=" + text;
            $.get(action, function (result) {
                debug("ajax get result=" + result);
                if (result.code !== 0) {
                    console.log("Error Ajax Get " + action + ": " + result);
                    return
                }
                var data = result.data || {};
                var html = "<ul>";
                for (let city of data) {
                    var name = '';
                    if (city.ParentName.length > 0) {
                        name += city.ParentName + " - "
                    }
                    name += city.Name;
                    if (city.Code.length > 0) {
                        html += "<li data-code='" + city.Code + "'><a href=/weather/" + city.Code + ".html>" + name + "</a></li>";
                    } else {
                        //https://w.youthlin.com/cityList#p-2
                        html += "<li data-code='" + city.Id + "'><a href=/cityList#p-" + city.Id + ">" + name + " </a></li >";
                    }
                }
                $target.html(html + "</ul>");
                $target.show();
            }, "json");


        });
    });
}

function debug(msg) {
    //console.log(msg);
}

function showJson() {
    var j = $('.json');
    j.css('max-height', "500px");
    var t = j.text();
    //debug(t);
    if (t.length > 0) {
        var value = JSON.parse(t);
        if (value.DoneCities != null) {
            for (let c of value.DoneCities) {
                c.Name = "<a href='/weather/" + c.Code + ".html'>" + c.Name + "</a>";
                if (c.ParentCode.length > 0) {
                    c.ParentName = "<a href='/weather/" + c.ParentCode + ".html'>" + c.ParentName + "</a>";
                }
            }
        }
        t = JSON.stringify(value, null, 4);
        j.html(t);
    }
}
